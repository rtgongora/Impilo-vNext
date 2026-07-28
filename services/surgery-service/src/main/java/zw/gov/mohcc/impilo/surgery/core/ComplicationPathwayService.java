package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.integration.PctProblemContributionClient;
import zw.gov.mohcc.impilo.surgery.integration.PctProblemContributionClient.ContributionResult;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalComplicationPathwayEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalComplicationPathwayRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SB-1 — complication pathway instances (surgical domain pack §15, R8): recognised, graded,
 * owned, investigated, treated, disclosed and closed. Shares its {@code complication_type} and
 * {@code grade_code} vocabularies with procedures-service's content-side {@code
 * complication_class}/{@code clavien_dindo_grade} (P10) — one governed list, not a second
 * registry. On CLOSE, the resulting complication becomes a contribution to {@code pct_problems}
 * via the same {@link PctProblemContributionClient} S1 already built (CC-2: contribute, never
 * copy) — best-effort, matching that client's own fail-open posture.
 */
@Service
public class ComplicationPathwayService {

    private static final Set<String> COMPLICATION_TYPES = Set.of(
            "SURGICAL_SITE_INFECTION", "BLEEDING", "ANASTOMOTIC_LEAK", "DEEP_VEIN_THROMBOSIS",
            "PULMONARY_EMBOLISM", "WOUND_DEHISCENCE", "ORGAN_INJURY", "NERVE_INJURY",
            "ANAESTHESIA_COMPLICATION", "TRANSFUSION_REACTION", "DEVICE_MALFUNCTION",
            "RETAINED_FOREIGN_OBJECT", "WRONG_SITE_PROCEDURE", "READMISSION",
            "UNPLANNED_RETURN_TO_THEATRE", "SEPSIS", "RENAL_INJURY", "CARDIAC_EVENT", "DEATH");
    private static final Set<String> GRADE_CODES = Set.of("I", "II", "IIIA", "IIIB", "IVA", "IVB", "V");

    private final SurgicalComplicationPathwayRepository repository;
    private final SurgicalEpisodeRepository episodeRepository;
    private final PctProblemContributionClient pctClient;

    public ComplicationPathwayService(SurgicalComplicationPathwayRepository repository,
                                      SurgicalEpisodeRepository episodeRepository,
                                      PctProblemContributionClient pctClient) {
        this.repository = repository;
        this.episodeRepository = episodeRepository;
        this.pctClient = pctClient;
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

    @Transactional
    public Map<String, Object> recognise(UUID episodeId, Map<String, Object> body) {
        UUID tenant = currentTenant();
        SurgicalEpisodeEntity episode = episodeRepository.findByIdAndTenantId(episodeId, tenant)
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_EPISODE_NOT_FOUND", 404,
                        "No surgical episode " + episodeId));

        String type = body.get("complicationType") != null ? body.get("complicationType").toString() : null;
        if (type == null || !COMPLICATION_TYPES.contains(type)) {
            throw new SurgeryDomainException("INVALID_COMPLICATION_TYPE", 400,
                    "complicationType must be one of " + COMPLICATION_TYPES);
        }

        SurgicalComplicationPathwayEntity e = new SurgicalComplicationPathwayEntity();
        e.setTenantId(tenant);
        e.setSurgicalEpisodeId(episodeId);
        e.setComplicationType(type);
        e.setRecognisedBy(currentActor());
        e.setRecognisedAt(OffsetDateTime.now());
        e.setStatus("OPEN");
        e = repository.save(e);
        return toView(e);
    }

    @Transactional
    public Map<String, Object> grade(UUID pathwayId, String gradeCode) {
        if (gradeCode == null || !GRADE_CODES.contains(gradeCode)) {
            throw new SurgeryDomainException("INVALID_GRADE", 400,
                    "gradeCode must be one of " + GRADE_CODES);
        }
        SurgicalComplicationPathwayEntity e = requirePathway(pathwayId);
        e.setGradeCode(gradeCode);
        e.setGradedBy(currentActor());
        e.setGradedAt(OffsetDateTime.now());
        return toView(repository.save(e));
    }

    @Transactional
    public Map<String, Object> update(UUID pathwayId, Map<String, Object> body) {
        SurgicalComplicationPathwayEntity e = requirePathway(pathwayId);
        if (body.get("ownedBy") != null) e.setOwnedBy(body.get("ownedBy").toString());
        if (body.get("investigation") != null) e.setInvestigation(body.get("investigation").toString());
        if (body.get("treatment") != null) e.setTreatment(body.get("treatment").toString());
        return toView(repository.save(e));
    }

    /** R8 stage 6: disclosed to the patient/family — a required stage before closure. */
    @Transactional
    public Map<String, Object> disclose(UUID pathwayId, Map<String, Object> body) {
        SurgicalComplicationPathwayEntity e = requirePathway(pathwayId);
        e.setDisclosedAt(OffsetDateTime.now());
        e.setDisclosedBy(currentActor());
        if (body.get("disclosureNotes") != null) e.setDisclosureNotes(body.get("disclosureNotes").toString());
        return toView(repository.save(e));
    }

    /**
     * R8 stage 7: closed, with an outcome — refused unless graded, owned and disclosed
     * (mirrored by V005's own CHECK). Best-effort contribution of the resulting complication
     * into pct_problems, the same fail-open posture {@link PctProblemContributionClient} already
     * has.
     */
    @Transactional
    public Map<String, Object> close(UUID pathwayId, Map<String, Object> body) {
        SurgicalComplicationPathwayEntity e = requirePathway(pathwayId);
        String outcome = body.get("outcome") != null ? body.get("outcome").toString() : null;
        if (outcome == null || outcome.isBlank()) {
            throw new SurgeryDomainException("OUTCOME_REQUIRED", 400, "outcome is required to close a pathway");
        }
        if (e.getGradeCode() == null) {
            throw new SurgeryDomainException("PATHWAY_NOT_GRADED", 409, "Cannot close: not yet graded");
        }
        if (e.getOwnedBy() == null) {
            throw new SurgeryDomainException("PATHWAY_NOT_OWNED", 409, "Cannot close: no owning team/clinician recorded");
        }
        if (e.getDisclosedAt() == null) {
            throw new SurgeryDomainException("PATHWAY_NOT_DISCLOSED", 409, "Cannot close: not yet disclosed to the patient/family");
        }

        e.setOutcome(outcome);
        e.setStatus("CLOSED");
        e.setClosedAt(OffsetDateTime.now());
        e.setClosedBy(currentActor());

        SurgicalEpisodeEntity episode = episodeRepository.findById(e.getSurgicalEpisodeId()).orElse(null);
        if (episode != null) {
            ContributionResult result = pctClient.contributeCondition(
                    episode.getSubjectCpid(), episode.getJourneyId(),
                    episode.getEncounterId() != null ? episode.getEncounterId().toString() : null,
                    e.getComplicationType().replace('_', ' ') + " (Clavien-Dindo " + e.getGradeCode() + ") — " + outcome,
                    "CONFIRMED", e.getInvestigation());
            if (result.contributed()) {
                e.setPctProblemRef(result.problemId());
                e.setPctProblemContributedAt(OffsetDateTime.now());
            }
        }

        return toView(repository.save(e));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForEpisode(UUID episodeId) {
        return repository.findBySurgicalEpisodeIdAndTenantIdOrderByRecognisedAtDesc(episodeId, currentTenant())
                .stream().map(this::toView).toList();
    }

    private SurgicalComplicationPathwayEntity requirePathway(UUID pathwayId) {
        return repository.findByIdAndTenantId(pathwayId, currentTenant())
                .orElseThrow(() -> new SurgeryDomainException("COMPLICATION_PATHWAY_NOT_FOUND", 404,
                        "No complication pathway " + pathwayId));
    }

    private Map<String, Object> toView(SurgicalComplicationPathwayEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("surgicalEpisodeId", e.getSurgicalEpisodeId().toString());
        m.put("complicationType", e.getComplicationType());
        m.put("recognisedBy", e.getRecognisedBy());
        m.put("recognisedAt", e.getRecognisedAt().toString());
        m.put("gradeCode", e.getGradeCode());
        m.put("gradedBy", e.getGradedBy());
        m.put("ownedBy", e.getOwnedBy());
        m.put("investigation", e.getInvestigation());
        m.put("treatment", e.getTreatment());
        m.put("disclosedAt", e.getDisclosedAt() != null ? e.getDisclosedAt().toString() : null);
        m.put("disclosedBy", e.getDisclosedBy());
        m.put("disclosureNotes", e.getDisclosureNotes());
        m.put("outcome", e.getOutcome());
        m.put("status", e.getStatus());
        m.put("closedAt", e.getClosedAt() != null ? e.getClosedAt().toString() : null);
        m.put("closedBy", e.getClosedBy());
        m.put("pctProblemRef", e.getPctProblemRef() != null ? e.getPctProblemRef().toString() : null);
        return m;
    }
}
