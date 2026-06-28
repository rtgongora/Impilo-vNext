package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Outpatient problems-list management (PCT).
 *
 * <p>PCT is the SoR for the outpatient encounter; the problems list attaches to a subject (and optionally a
 * journey/encounter). Authorization is enforced upstream at Envoy ext_authz (policy {@code CARE-PLAN-WRITE}
 * family / track P).</p>
 */
@Service
public class ProblemService {

    private static final Logger log = LoggerFactory.getLogger(ProblemService.class);

    private final ProblemRepository problemRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;

    public ProblemService(ProblemRepository problemRepository,
                          EventOutboxRepository outboxRepository,
                          ObjectMapper objectMapper,
                          ClinicalAccessGuard accessGuard) {
        this.problemRepository = problemRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<ProblemEntity> list(String subjectCpid, String clinicalStatus) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        if (clinicalStatus == null || clinicalStatus.isBlank()) {
            return problemRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(tenantId, subjectCpid);
        }
        return problemRepository.findByTenantIdAndSubjectCpidAndClinicalStatusOrderByCreatedAtDesc(
                tenantId, subjectCpid, clinicalStatus.trim().toUpperCase(Locale.ROOT));
    }

    @Transactional
    public ProblemEntity add(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        ProblemEntity p = new ProblemEntity();
        p.setTenantId(ctx.tenantId());
        p.setSubjectCpid(required(body, "subject_cpid"));
        p.setJourneyId(str(body.get("journey_id")));
        p.setEncounterId(str(body.get("encounter_id")));
        // Subject-relationship gate (dimension 6): the actor must hold an active care context for
        // this patient. ext_authz enforces RBAC but cannot bind the body subject to consent/relationship.
        accessGuard.requireCareRelationship(ctx, p.getSubjectCpid(), p.getJourneyId(), p.getEncounterId());
        p.setCode(str(body.get("code")));
        p.setCodeSystem(str(body.get("code_system")));
        p.setDisplay(required(body, "display"));
        p.setCategory(upperOr(body.get("category"), "DIAGNOSIS"));
        p.setClinicalStatus(upperOr(body.get("clinical_status"), "ACTIVE"));
        String onset = str(body.get("onset_date"));
        if (onset != null) {
            p.setOnsetDate(LocalDate.parse(onset));
        }
        p.setNotes(str(body.get("notes")));
        p.setRecordedBy(ctx.actorId());
        p = problemRepository.save(p);

        emit("PROBLEM_ADDED", p);
        log.info("pct.problem.added id={} subject={} status={} by={} correlationId={}",
                p.getProblemId(), p.getSubjectCpid(), p.getClinicalStatus(), ctx.actorId(), ctx.correlationId());
        return p;
    }

    @Transactional
    public ProblemEntity resolve(UUID problemId) {
        TrustContext ctx = TrustContextHolder.require();
        ProblemEntity p = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + problemId));
        if (!p.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Problem not found: " + problemId);
        }
        p.setClinicalStatus("RESOLVED");
        p.setResolvedAt(OffsetDateTime.now());
        p = problemRepository.save(p);
        emit("PROBLEM_RESOLVED", p);
        log.info("pct.problem.resolved id={} subject={} by={} correlationId={}",
                p.getProblemId(), p.getSubjectCpid(), ctx.actorId(), ctx.correlationId());
        return p;
    }

    private void emit(String eventType, ProblemEntity p) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("problemId", p.getProblemId().toString());
        payload.put("subjectCpid", p.getSubjectCpid());
        payload.put("journeyId", p.getJourneyId());
        payload.put("display", p.getDisplay());
        payload.put("clinicalStatus", p.getClinicalStatus());
        payload.put("actorId", ctx.actorId());
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("PROBLEM");
        outbox.setAggregateId(p.getProblemId().toString());
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return String.valueOf(v).trim();
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String upperOr(Object v, String def) {
        String s = str(v);
        return s == null ? def : s.toUpperCase(Locale.ROOT);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise problem payload: {}", e.getMessage());
            return "{}";
        }
    }
}
