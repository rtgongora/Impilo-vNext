package zw.gov.mohcc.impilo.surgery.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surgery.api.dto.SurgicalEpisodeDtos.OpenEpisodeRequest;
import zw.gov.mohcc.impilo.surgery.api.dto.SurgicalEpisodeDtos.SurgicalEpisodeView;
import zw.gov.mohcc.impilo.surgery.integration.InpatientSpecimenClient;
import zw.gov.mohcc.impilo.surgery.integration.InpatientSpecimenClient.SpecimenGateView;
import zw.gov.mohcc.impilo.surgery.integration.PctProblemContributionClient;
import zw.gov.mohcc.impilo.surgery.integration.PctProblemContributionClient.ContributionResult;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S1 — the surgical episode. A management-course record that attaches to PCT journeys and never
 * contains them (component test: reference, contribute, execute, stamp — see
 * {@code SurgeryApplication}'s own javadoc and V002's migration header for the full rationale).
 *
 * <p>This service does three things and deliberately nothing else:</p>
 * <ul>
 *   <li><b>Reference</b> — carries a PCT anchor (journey/encounter) and, once one exists, a
 *       reference to {@code inpatient.procedure_episode}. Never a copy of either.</li>
 *   <li><b>Contribute</b> — calls {@link PctProblemContributionClient} to write the surgical
 *       condition into {@code pct_problems} with {@code responsible_service=surgery}, best-effort
 *       (a PCT outage never blocks recording the episode locally).</li>
 *   <li><b>Execute, never decide</b> — records the surgical REASONING (operative indication, the
 *       non-operative options weighed) and this service's OWN view of that reasoning's progress.
 *       It never claims to open or close a phase of care: where an operation needs inpatient
 *       admission, that continues through the unmodified admission-handshake in
 *       inpatient-service, untouched by this class.</li>
 * </ul>
 */
@Service
public class SurgicalEpisodeService {

    private static final List<String> STATUSES =
            List.of("ASSESSMENT", "LISTED_FOR_SURGERY", "OPERATED", "CLOSED", "ABANDONED");

    private final SurgicalEpisodeRepository repository;
    private final PctProblemContributionClient pctClient;
    private final InpatientSpecimenClient specimenClient;

    public SurgicalEpisodeService(SurgicalEpisodeRepository repository, PctProblemContributionClient pctClient,
                                  InpatientSpecimenClient specimenClient) {
        this.repository = repository;
        this.pctClient = pctClient;
        this.specimenClient = specimenClient;
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
    public SurgicalEpisodeView openEpisode(OpenEpisodeRequest req) {
        if (req.subjectCpid() == null || req.subjectCpid().isBlank()) {
            throw new SurgeryDomainException("SUBJECT_CPID_REQUIRED", 400, "subjectCpid is required");
        }
        if ((req.journeyId() == null || req.journeyId().isBlank())
                && (req.encounterId() == null || req.encounterId().isBlank())) {
            throw new SurgeryDomainException("PCT_ANCHOR_REQUIRED", 400,
                    "A resolvable PCT anchor is required — journeyId or encounterId (care-continuum-doctrine CC-5)");
        }
        if (req.operativeIndication() == null || req.operativeIndication().isBlank()) {
            throw new SurgeryDomainException("OPERATIVE_INDICATION_REQUIRED", 400,
                    "operativeIndication is required — the surgical reasoning this service exists to record");
        }

        SurgicalEpisodeEntity e = new SurgicalEpisodeEntity();
        e.setTenantId(currentTenant());
        e.setSubjectCpid(req.subjectCpid());
        e.setJourneyId(req.journeyId());
        if (req.encounterId() != null && !req.encounterId().isBlank()) {
            e.setEncounterId(UUID.fromString(req.encounterId()));
        }
        e.setOperativeIndication(req.operativeIndication());
        e.setNonOperativeOptionsConsidered(req.nonOperativeOptionsConsidered());
        e.setSpecialty(req.specialty());
        e.setCreatedBy(currentActor());
        e.setStatus("ASSESSMENT");
        e = repository.save(e);

        // Best-effort contribution — a PCT outage must not block recording the episode, which
        // stays the local truth either way. idx_surgical_episode_uncontributed is the
        // reconciliation query for episodes where this has not yet succeeded.
        if (req.conditionDisplay() != null && !req.conditionDisplay().isBlank()) {
            ContributionResult result = pctClient.contributeCondition(
                    req.subjectCpid(), req.journeyId(), req.encounterId(),
                    req.conditionDisplay(), req.diagnosticCertainty(), req.evidence());
            if (result.contributed()) {
                e.setPctProblemRef(result.problemId());
                e.setPctProblemContributedAt(OffsetDateTime.now());
                e = repository.save(e);
            }
        }

        return toView(e);
    }

    @Transactional
    public SurgicalEpisodeView linkProcedureEpisode(UUID episodeId, UUID procedureEpisodeRef) {
        SurgicalEpisodeEntity e = requireEpisode(episodeId);
        e.setProcedureEpisodeRef(procedureEpisodeRef);
        return toView(repository.save(e));
    }

    @Transactional
    public SurgicalEpisodeView transition(UUID episodeId, String newStatus) {
        if (!STATUSES.contains(newStatus)) {
            throw new SurgeryDomainException("INVALID_STATUS", 400,
                    "status must be one of " + STATUSES);
        }
        SurgicalEpisodeEntity e = requireEpisode(episodeId);
        if ("CLOSED".equals(newStatus)) {
            requireHistologyReviewed(e);
        }
        e.setStatus(newStatus);
        return toView(repository.save(e));
    }

    /**
     * §16 histology closure gate (SB-1): a surgical episode may not CLOSE while any specimen from
     * its linked operation has histology in flight or a result nobody has reviewed. Fail-SAFE,
     * deliberately opposite to the PCT contribution's fail-open posture: an unanswerable specimen
     * question blocks closure — an unreachable specimen list is not an empty one. An episode with
     * no linked operation (e.g. assessed, decision DO_NOT_PROCEED) has no specimens to gate on and
     * closes freely.
     */
    private void requireHistologyReviewed(SurgicalEpisodeEntity e) {
        if (e.getProcedureEpisodeRef() == null) {
            return;
        }
        SpecimenGateView gate = specimenClient.specimenGate(e.getProcedureEpisodeRef());
        if (!gate.known()) {
            throw new SurgeryDomainException("SPECIMEN_STATE_UNKNOWN", 503,
                    "Cannot close: the linked operation's specimen state is unavailable — "
                            + "an unknown histology status blocks closure rather than clearing it");
        }
        if (!gate.allReviewed()) {
            throw new SurgeryDomainException("HISTOLOGY_UNREVIEWED", 409,
                    "Cannot close: " + gate.unreviewed() + " of " + gate.total()
                            + " specimen(s) from the linked operation have histology pending or an "
                            + "unacknowledged result. Review and acknowledge every result first.");
        }
    }

    @Transactional(readOnly = true)
    public SurgicalEpisodeView getEpisode(UUID episodeId) {
        return toView(requireEpisode(episodeId));
    }

    @Transactional(readOnly = true)
    public List<SurgicalEpisodeView> episodesForSubject(String subjectCpid) {
        return repository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(currentTenant(), subjectCpid).stream()
                .map(this::toView).toList();
    }

    private SurgicalEpisodeEntity requireEpisode(UUID episodeId) {
        return repository.findByIdAndTenantId(episodeId, currentTenant())
                .orElseThrow(() -> new SurgeryDomainException("SURGICAL_EPISODE_NOT_FOUND", 404,
                        "No surgical episode " + episodeId));
    }

    private SurgicalEpisodeView toView(SurgicalEpisodeEntity e) {
        return new SurgicalEpisodeView(
                e.getId().toString(),
                e.getSubjectCpid(),
                e.getJourneyId(),
                e.getEncounterId() != null ? e.getEncounterId().toString() : null,
                e.getProcedureEpisodeRef() != null ? e.getProcedureEpisodeRef().toString() : null,
                e.getPctProblemRef() != null ? e.getPctProblemRef().toString() : null,
                e.getOperativeIndication(),
                e.getNonOperativeOptionsConsidered(),
                e.getStatus(),
                e.getSpecialty(),
                e.getPctProblemRef() != null);
    }
}
