package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.CarePlanEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.CarePlanGoalEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.CarePlanGoalRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.CarePlanRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
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
 * Outpatient care-plan management (PCT) — parity with inpatient-service CarePlan for the OPD context.
 *
 * <p>Authorization is enforced upstream at Envoy ext_authz (policy {@code CARE-PLAN-WRITE} / track P).</p>
 */
@Service
public class CarePlanService {

    private static final Logger log = LoggerFactory.getLogger(CarePlanService.class);

    private final CarePlanRepository planRepository;
    private final CarePlanGoalRepository goalRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;

    public CarePlanService(CarePlanRepository planRepository,
                           CarePlanGoalRepository goalRepository,
                           EventOutboxRepository outboxRepository,
                           ObjectMapper objectMapper,
                           ClinicalAccessGuard accessGuard) {
        this.planRepository = planRepository;
        this.goalRepository = goalRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<CarePlanEntity> listForSubject(String subjectCpid) {
        return planRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(
                TrustContextHolder.require().tenantId(), subjectCpid);
    }

    @Transactional(readOnly = true)
    public CarePlanEntity get(UUID planId) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        CarePlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Care plan not found: " + planId));
        if (!plan.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Care plan not found: " + planId);
        }
        return plan;
    }

    @Transactional(readOnly = true)
    public List<CarePlanGoalEntity> goals(UUID planId) {
        get(planId); // tenant guard
        return goalRepository.findByPlanIdOrderByCreatedAtAsc(planId);
    }

    @Transactional
    public CarePlanEntity create(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        CarePlanEntity plan = new CarePlanEntity();
        plan.setTenantId(ctx.tenantId());
        plan.setSubjectCpid(required(body, "subject_cpid"));
        plan.setJourneyId(str(body.get("journey_id")));
        plan.setEncounterId(str(body.get("encounter_id")));
        // Subject-relationship gate (dimension 6): the actor must hold an active care context for
        // this patient. ext_authz enforces RBAC but cannot bind the body subject to consent/relationship.
        accessGuard.requireCareRelationship(ctx, plan.getSubjectCpid(), plan.getJourneyId(), plan.getEncounterId());
        plan.setTitle(required(body, "title"));
        plan.setPlanType(upperOr(body.get("plan_type"), "OUTPATIENT"));
        plan.setStatus(upperOr(body.get("status"), "ACTIVE"));
        plan.setCreatedBy(ctx.actorId());
        plan = planRepository.save(plan);
        // Inline goals (parity with inpatient create): accept either a list of strings
        // ("goals": ["..."]) or a list of objects ([{description, category, ...}]).
        if (body.get("goals") instanceof List<?> goals) {
            for (Object g : goals) {
                if (g == null) {
                    continue;
                }
                CarePlanGoalEntity goal = new CarePlanGoalEntity();
                goal.setPlanId(plan.getPlanId());
                goal.setTenantId(plan.getTenantId());
                if (g instanceof Map<?, ?> gm) {
                    goal.setDescription(str(gm.get("description")));
                    goal.setCategory(upperOr(gm.get("category"), "CLINICAL"));
                    goal.setPriority(upperOr(gm.get("priority"), "MEDIUM"));
                    goal.setStatus(upperOr(gm.get("status"), "IN_PROGRESS"));
                } else {
                    goal.setDescription(g.toString());
                    goal.setCategory("CLINICAL");
                    goal.setPriority("MEDIUM");
                    goal.setStatus("IN_PROGRESS");
                }
                if (goal.getDescription() != null && !goal.getDescription().isBlank()) {
                    goalRepository.save(goal);
                }
            }
        }
        emit("CARE_PLAN_CREATED", "CARE_PLAN", plan.getPlanId().toString(), planPayload(plan));
        log.info("pct.care_plan.created id={} subject={} by={} correlationId={}",
                plan.getPlanId(), plan.getSubjectCpid(), ctx.actorId(), ctx.correlationId());
        return plan;
    }

    @Transactional
    public CarePlanEntity updateStatus(UUID planId, String status) {
        CarePlanEntity plan = get(planId);
        plan.setStatus(status.trim().toUpperCase(Locale.ROOT));
        plan = planRepository.save(plan);
        emit("CARE_PLAN_UPDATED", "CARE_PLAN", plan.getPlanId().toString(), planPayload(plan));
        return plan;
    }

    @Transactional
    public CarePlanGoalEntity addGoal(UUID planId, Map<String, Object> body) {
        CarePlanEntity plan = get(planId);
        CarePlanGoalEntity goal = new CarePlanGoalEntity();
        goal.setPlanId(plan.getPlanId());
        goal.setTenantId(plan.getTenantId());
        goal.setCategory(upperOr(body.get("category"), "CLINICAL"));
        goal.setDescription(required(body, "description"));
        goal.setTargetValue(str(body.get("target_value")));
        goal.setCurrentValue(str(body.get("current_value")));
        goal.setPriority(upperOr(body.get("priority"), "MEDIUM"));
        goal.setStatus(upperOr(body.get("status"), "IN_PROGRESS"));
        String td = str(body.get("target_date"));
        if (td != null) {
            goal.setTargetDate(LocalDate.parse(td));
        }
        goal = goalRepository.save(goal);
        emit("CARE_PLAN_GOAL_ADDED", "CARE_PLAN", plan.getPlanId().toString(), goalPayload(goal));
        return goal;
    }

    @Transactional
    public CarePlanGoalEntity updateGoalStatus(UUID planId, UUID goalId, String status) {
        get(planId); // tenant guard
        CarePlanGoalEntity goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        if (!goal.getPlanId().equals(planId)) {
            throw new IllegalArgumentException("Goal not found on plan: " + goalId);
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        goal.setStatus(s);
        if ("ACHIEVED".equals(s)) {
            goal.setAchievedAt(OffsetDateTime.now());
        }
        goal = goalRepository.save(goal);
        emit("CARE_PLAN_GOAL_UPDATED", "CARE_PLAN", planId.toString(), goalPayload(goal));
        return goal;
    }

    private Map<String, Object> planPayload(CarePlanEntity plan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planId", plan.getPlanId().toString());
        m.put("subjectCpid", plan.getSubjectCpid());
        m.put("journeyId", plan.getJourneyId());
        m.put("status", plan.getStatus());
        m.put("planType", plan.getPlanType());
        return m;
    }

    private Map<String, Object> goalPayload(CarePlanGoalEntity goal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("goalId", goal.getGoalId().toString());
        m.put("planId", goal.getPlanId().toString());
        m.put("status", goal.getStatus());
        m.put("category", goal.getCategory());
        return m;
    }

    private void emit(String eventType, String aggregateType, String aggregateId, Map<String, Object> payload) {
        TrustContext ctx = TrustContextHolder.require();
        payload.put("actorId", ctx.actorId());
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
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
            log.warn("Failed to serialise care-plan payload: {}", e.getMessage());
            return "{}";
        }
    }
}
