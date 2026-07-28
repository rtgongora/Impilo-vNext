package zw.gov.mohcc.impilo.inpatient.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.integration.ButanoProcedureClient;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureEpisodeEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureSpecimenEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureEpisodeRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureSpecimenRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chain of custody, label confirmation and adequacy for a theatre specimen (pipeline §13).
 *
 * <p>{@code procedure_specimen} (V022) is theatre's own LINK/PROJECTION table — OROS stays the
 * specimen SoR. This is a separate, small service rather than another method on
 * {@code TheatreService} (already a large aggregate root many lanes touch concurrently) for the
 * same reason P6 put competence resolution in its own class: a narrow, independently testable
 * surface with its own repository dependency.</p>
 *
 * <p><b>Honest scope</b> (see V301's own header): these methods RECORD what a real person
 * attests — collection, label confirmation, custody receipt, adequacy. Nothing here is called
 * automatically by {@code TheatreService.processSpecimensFromNote}, which parses and dispatches
 * a specimen in one automated step with no human confirmation point to hang a gate on. This is a
 * recording capability, not (yet) a blocking gate on the automatic note-parse path — closing that
 * gap is theatre/emergency-lane workflow, not a unilateral change from this class.</p>
 */
@Service
public class SpecimenCustodyService {

    private static final Set<String> ADEQUACY_VALUES = Set.of("NOT_ASSESSED", "ADEQUATE", "INADEQUATE");

    private final ProcedureSpecimenRepository specimenRepository;
    private final ProcedureEpisodeRepository episodeRepository;
    private final ButanoProcedureClient butanoClient;

    public SpecimenCustodyService(ProcedureSpecimenRepository specimenRepository,
                                  ProcedureEpisodeRepository episodeRepository,
                                  ButanoProcedureClient butanoClient) {
        this.specimenRepository = specimenRepository;
        this.episodeRepository = episodeRepository;
        this.butanoClient = butanoClient;
    }

    private UUID currentTenant() {
        return TrustContextHolder.require().tenantId();
    }

    private String currentActor() {
        try {
            String actor = TrustContextHolder.require().actorId();
            return actor != null && !actor.isBlank() ? actor : "system";
        } catch (IllegalStateException e) {
            return "system";
        }
    }

    /** Loads a specimen, refusing one that belongs to a different tenant or a different episode. */
    private ProcedureSpecimenEntity requireSpecimen(UUID episodeId, UUID specimenId) {
        ProcedureSpecimenEntity s = specimenRepository.findById(specimenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No specimen " + specimenId));
        if (!s.getTenantId().equals(currentTenant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No specimen " + specimenId);
        }
        if (!s.getEpisodeId().equals(episodeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Specimen " + specimenId + " does not belong to episode " + episodeId);
        }
        return s;
    }

    @Transactional(readOnly = true)
    public List<ProcedureSpecimenEntity> specimensForEpisode(UUID episodeId) {
        return specimenRepository.findByEpisodeIdOrderBySeqAsc(episodeId).stream()
                .filter(s -> s.getTenantId().equals(currentTenant()))
                .toList();
    }

    /**
     * Records who physically collected the specimen and its container/fixative, and when. Also
     * writes a real FHIR {@code Specimen} resource (pipeline §24) — this is the first point real
     * collector/container data exists to put in one; writing it earlier (at note-parse time, with
     * only a free-text label) would have nothing genuine to report. Best-effort: a Butano outage
     * must not block recording custody, which remains the local truth either way.
     */
    @Transactional
    public ProcedureSpecimenEntity recordCollection(UUID episodeId, UUID specimenId,
                                                     String containerType, String fixative) {
        ProcedureSpecimenEntity s = requireSpecimen(episodeId, specimenId);
        s.setCollectedBy(currentActor());
        s.setCollectedAt(OffsetDateTime.now());
        s.setContainerType(containerType);
        s.setFixative(fixative);
        episodeRepository.findById(episodeId).ifPresent(episode -> {
            String fhirRef = butanoClient.writeSpecimen(episode.getSubjectCpid(), s);
            if (fhirRef != null) {
                s.setFhirSpecimenRef(fhirRef);
            }
        });
        return specimenRepository.save(s);
    }

    /**
     * A named person attests the label on the container matches the patient it was taken from —
     * the label-mismatch prevention step the pipeline audit found absent (§13).
     */
    @Transactional
    public ProcedureSpecimenEntity confirmLabel(UUID episodeId, UUID specimenId) {
        ProcedureSpecimenEntity s = requireSpecimen(episodeId, specimenId);
        s.setLabelConfirmedBy(currentActor());
        s.setLabelConfirmedAt(OffsetDateTime.now());
        return specimenRepository.save(s);
    }

    /** Custody handoff at the receiving end (the lab) — a person taking responsibility, not a courier leg. */
    @Transactional
    public ProcedureSpecimenEntity recordReceipt(UUID episodeId, UUID specimenId) {
        ProcedureSpecimenEntity s = requireSpecimen(episodeId, specimenId);
        s.setReceivedBy(currentActor());
        s.setReceivedAt(OffsetDateTime.now());
        return specimenRepository.save(s);
    }

    /** Was there enough tissue/fluid to report on. ADEQUATE/INADEQUATE only — not a default. */
    @Transactional
    public ProcedureSpecimenEntity assessAdequacy(UUID episodeId, UUID specimenId, String adequacy) {
        if (adequacy == null || !ADEQUACY_VALUES.contains(adequacy) || "NOT_ASSESSED".equals(adequacy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "adequacy must be ADEQUATE or INADEQUATE");
        }
        ProcedureSpecimenEntity s = requireSpecimen(episodeId, specimenId);
        s.setAdequacy(adequacy);
        s.setAdequacyAssessedBy(currentActor());
        s.setAdequacyAssessedAt(OffsetDateTime.now());
        return specimenRepository.save(s);
    }

    /** JSON row shape for the custody fields, matching TheatreService's own specimenRow idiom. */
    public Map<String, Object> custodyRow(ProcedureSpecimenEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getSpecimenId().toString());
        m.put("specimen_label", s.getSpecimenLabel());
        m.put("status", s.getStatus());
        m.put("collected_by", s.getCollectedBy());
        m.put("collected_at", s.getCollectedAt());
        m.put("container_type", s.getContainerType());
        m.put("fixative", s.getFixative());
        m.put("label_confirmed_by", s.getLabelConfirmedBy());
        m.put("label_confirmed_at", s.getLabelConfirmedAt());
        m.put("received_by", s.getReceivedBy());
        m.put("received_at", s.getReceivedAt());
        m.put("adequacy", s.getAdequacy());
        m.put("adequacy_assessed_by", s.getAdequacyAssessedBy());
        m.put("adequacy_assessed_at", s.getAdequacyAssessedAt());
        m.put("fhir_specimen_ref", s.getFhirSpecimenRef());
        return m;
    }
}
