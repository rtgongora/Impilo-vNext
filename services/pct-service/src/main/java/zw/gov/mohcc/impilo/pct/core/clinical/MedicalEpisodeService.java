package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicalEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicalEpisodeProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicalEpisodeProblemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicalEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Medical episodes — the course of care a problem is managed through.
 *
 * <p>PCT owns the Care Continuum (care-continuum-doctrine), so the longitudinal course of medical
 * care belongs here rather than in a specialty service. The episode gathers journeys and problems;
 * it never contains the continuum and never rivals {@code pct.emergency_episode}, which records a
 * single presentation to emergency care and which this links to.</p>
 */
@Service
public class MedicalEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(MedicalEpisodeService.class);

    private final MedicalEpisodeRepository episodeRepository;
    private final MedicalEpisodeProblemRepository episodeProblemRepository;
    private final ProblemRepository problemRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;

    public MedicalEpisodeService(MedicalEpisodeRepository episodeRepository,
                                 MedicalEpisodeProblemRepository episodeProblemRepository,
                                 ProblemRepository problemRepository,
                                 EventOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper,
                                 ClinicalAccessGuard accessGuard) {
        this.episodeRepository = episodeRepository;
        this.episodeProblemRepository = episodeProblemRepository;
        this.problemRepository = problemRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<MedicalEpisodeEntity> list(String subjectCpid, boolean openOnly) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return openOnly
                ? episodeRepository.findByTenantIdAndSubjectCpidAndStatusInOrderByStartedOnDesc(
                        tenantId, subjectCpid, MedicalEpisodeVocabulary.OPEN_STATUSES)
                : episodeRepository.findByTenantIdAndSubjectCpidOrderByStartedOnDesc(tenantId, subjectCpid);
    }

    @Transactional(readOnly = true)
    public List<MedicalEpisodeProblemEntity> problemsOf(UUID episodeId) {
        return episodeProblemRepository.findByIdEpisodeId(episodeId);
    }

    @Transactional
    public MedicalEpisodeEntity open(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        MedicalEpisodeEntity e = new MedicalEpisodeEntity();
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(required(body, "subject_cpid", "subjectCpid"));
        accessGuard.requireCareRelationship(ctx, e.getSubjectCpid(), str(body.get("journey_id"), body.get("journeyId")),
                str(body.get("encounter_id"), body.get("encounterId")));

        e.setEpisodeType(ProblemVocabulary.require("episode_type",
                str(body.get("episode_type"), body.get("episodeType")),
                MedicalEpisodeVocabulary.TYPES, true));
        e.setStatus(ProblemVocabulary.require("status",
                body.getOrDefault("status", "ACTIVE"), MedicalEpisodeVocabulary.STATUSES, true));
        e.setTitle(required(body, "title", "title"));
        e.setResponsibleService(str(body.get("responsible_service"), body.get("responsibleService")));
        e.setResponsibleActor(str(body.get("responsible_actor"), body.get("responsibleActor")));
        e.setManagingFacilityId(str(body.get("managing_facility_id"), body.get("managingFacilityId")));
        e.setNotes(str(body.get("notes")));
        e.setCreatedBy(ctx.actorId());

        String startedOn = str(body.get("started_on"), body.get("startedOn"));
        e.setStartedOn(startedOn == null ? LocalDate.now() : LocalDate.parse(startedOn));

        String emergencyEpisodeId = str(body.get("emergency_episode_id"), body.get("emergencyEpisodeId"));
        if (emergencyEpisodeId != null) {
            e.setEmergencyEpisodeId(UUID.fromString(emergencyEpisodeId));
        }

        // An episode opened directly as closed still has to say when and why it ended, or the
        // database CHECK refuses it with a message nobody can act on.
        if (MedicalEpisodeVocabulary.CLOSED_STATUSES.contains(e.getStatus())) {
            throw new IllegalArgumentException(
                    "An episode cannot be opened in status " + e.getStatus() + ". Open it, then close it.");
        }

        e = episodeRepository.save(e);
        emit("MEDICAL_EPISODE_OPENED", e);
        log.info("pct.medical_episode.opened id={} subject={} type={} by={} correlationId={}",
                e.getEpisodeId(), e.getSubjectCpid(), e.getEpisodeType(), ctx.actorId(), ctx.correlationId());
        return e;
    }

    /**
     * Attaches a problem to the episode.
     *
     * <p>The problem must already exist and belong to the same patient. Creating one implicitly
     * here would be a second write path into the problem list, bypassing the duplicate guard — the
     * exact fork that guard exists to prevent.</p>
     */
    @Transactional
    public MedicalEpisodeProblemEntity attachProblem(UUID episodeId, UUID problemId, String requestedRole) {
        TrustContext ctx = TrustContextHolder.require();
        MedicalEpisodeEntity episode = requireEpisode(episodeId, ctx);
        ProblemEntity problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + problemId));

        if (!problem.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Problem not found: " + problemId);
        }
        // A problem belonging to a different patient on this episode would silently attribute one
        // person's diagnosis to another's course of care.
        if (!problem.getSubjectCpid().equals(episode.getSubjectCpid())) {
            throw new IllegalArgumentException(
                    "Problem " + problemId + " belongs to a different patient than episode " + episodeId);
        }

        MedicalEpisodeProblemEntity link = new MedicalEpisodeProblemEntity();
        link.setId(new MedicalEpisodeProblemEntity.Key(episodeId, problemId));
        link.setTenantId(ctx.tenantId());
        link.setRole(ProblemVocabulary.require("role",
                requestedRole == null ? "PRIMARY" : requestedRole,
                MedicalEpisodeVocabulary.PROBLEM_ROLES, true));
        link = episodeProblemRepository.save(link);

        log.info("pct.medical_episode.problem_attached episode={} problem={} role={} by={}",
                episodeId, problemId, link.getRole(), ctx.actorId());
        return link;
    }

    /**
     * Closes the episode.
     *
     * <p>The end reason is required rather than defaulted. "Completed", "the patient died",
     * "transferred" and "lost to follow-up" all leave the status FINISHED and mean entirely
     * different things — defaulting to COMPLETED would record a good outcome for a patient who was
     * lost, and every outcome indicator built on this table would inherit that lie.</p>
     */
    @Transactional
    public MedicalEpisodeEntity close(UUID episodeId, String requestedStatus, String requestedEndReason,
                                      String endedOn) {
        TrustContext ctx = TrustContextHolder.require();
        MedicalEpisodeEntity e = requireEpisode(episodeId, ctx);

        String status = ProblemVocabulary.require("status",
                requestedStatus == null ? "FINISHED" : requestedStatus,
                MedicalEpisodeVocabulary.STATUSES, true);
        if (!MedicalEpisodeVocabulary.CLOSED_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "Closing status must be one of " + MedicalEpisodeVocabulary.CLOSED_STATUSES
                    + ", not " + status);
        }
        String endReason = ProblemVocabulary.require("end_reason",
                requestedEndReason, MedicalEpisodeVocabulary.END_REASONS, true);

        LocalDate endDate = endedOn == null ? LocalDate.now() : LocalDate.parse(endedOn);
        if (endDate.isBefore(e.getStartedOn())) {
            throw new IllegalArgumentException(
                    "An episode cannot end (" + endDate + ") before it started (" + e.getStartedOn() + ").");
        }

        e.setStatus(status);
        e.setEndReason(endReason);
        e.setEndedOn(endDate);
        e = episodeRepository.save(e);

        emit("MEDICAL_EPISODE_CLOSED", e);
        log.info("pct.medical_episode.closed id={} subject={} status={} reason={} by={} correlationId={}",
                e.getEpisodeId(), e.getSubjectCpid(), status, endReason, ctx.actorId(), ctx.correlationId());
        return e;
    }

    /** Reopens a closed episode, clearing the end so the record does not claim two truths. */
    @Transactional
    public MedicalEpisodeEntity reopen(UUID episodeId) {
        TrustContext ctx = TrustContextHolder.require();
        MedicalEpisodeEntity e = requireEpisode(episodeId, ctx);
        e.setStatus("ACTIVE");
        e.setEndedOn(null);
        e.setEndReason(null);
        e = episodeRepository.save(e);
        emit("MEDICAL_EPISODE_REOPENED", e);
        log.info("pct.medical_episode.reopened id={} subject={} by={}",
                e.getEpisodeId(), e.getSubjectCpid(), ctx.actorId());
        return e;
    }

    private MedicalEpisodeEntity requireEpisode(UUID episodeId, TrustContext ctx) {
        MedicalEpisodeEntity e = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new IllegalArgumentException("Episode not found: " + episodeId));
        if (!e.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Episode not found: " + episodeId);
        }
        return e;
    }

    private void emit(String eventType, MedicalEpisodeEntity e) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("episodeId", e.getEpisodeId().toString());
        payload.put("subjectCpid", e.getSubjectCpid());
        payload.put("episodeType", e.getEpisodeType());
        payload.put("status", e.getStatus());
        payload.put("endReason", e.getEndReason());
        payload.put("responsibleService", e.getResponsibleService());
        payload.put("emergencyEpisodeId",
                e.getEmergencyEpisodeId() == null ? null : e.getEmergencyEpisodeId().toString());
        payload.put("actorId", ctx.actorId());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("MEDICAL_EPISODE");
        outbox.setAggregateId(e.getEpisodeId().toString());
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    /** Required field, readable in either spelling; names the snake_case form when absent. */
    private static String required(Map<String, Object> body, String snakeKey, String camelKey) {
        String v = str(body.get(snakeKey), body.get(camelKey));
        if (v == null) {
            throw new IllegalArgumentException("Missing field: " + snakeKey);
        }
        return v;
    }

    /**
     * First non-blank of the candidates, so every multi-word key is readable in both spellings.
     * An optional field read snake-only is silently null when a caller sends camelCase — a 201
     * with the clinical value missing. See ClinicalKeySpellingTest, which enforces this.
     */
    private static String str(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialise medical episode payload: {}", ex.getMessage());
            return "{}";
        }
    }
}
