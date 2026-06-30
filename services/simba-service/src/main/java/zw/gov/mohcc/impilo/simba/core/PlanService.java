package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.EnrollmentTaskEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.PlanEnrollmentEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.PlanTaskEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.WellnessPlanEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.EnrollmentTaskRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.PlanEnrollmentRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.PlanTaskRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.WellnessPlanRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness plans / journeys: governed plan catalogue, eligibility-gated enrollment with task
 * instantiation, and per-task progress that recomputes the enrollment completion percentage.
 *
 * <p>Plans/journeys are a Simba-owned wellness capability. Plans flagged {@code clinicalCaution}
 * surface a care/screening prompt at enrolment (the controller/UI acts on the returned flag);
 * Simba never creates a clinical record — risk routes to PCT via {@link CareLinkageService}.</p>
 */
@Service
public class PlanService {

    private final WellnessPlanRepository planRepository;
    private final PlanTaskRepository planTaskRepository;
    private final PlanEnrollmentRepository enrollmentRepository;
    private final EnrollmentTaskRepository enrollmentTaskRepository;
    private final SimbaEventEmitter events;

    public PlanService(WellnessPlanRepository planRepository,
                       PlanTaskRepository planTaskRepository,
                       PlanEnrollmentRepository enrollmentRepository,
                       EnrollmentTaskRepository enrollmentTaskRepository,
                       SimbaEventEmitter events) {
        this.planRepository = planRepository;
        this.planTaskRepository = planTaskRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentTaskRepository = enrollmentTaskRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<WellnessPlanEntity> listPlans(UUID tenantId) {
        return planRepository.findByTenantIdAndStatusOrderByNameAsc(tenantId, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public WellnessPlanEntity getPlan(UUID tenantId, UUID planId) {
        return planRepository.findByPlanIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
    }

    @Transactional(readOnly = true)
    public List<PlanTaskEntity> planTasks(UUID planId) {
        return planTaskRepository.findByPlanIdOrderByStageAscSeqAsc(planId);
    }

    @Transactional(readOnly = true)
    public List<PlanEnrollmentEntity> listEnrollments(UUID tenantId, String personCpid) {
        return enrollmentRepository.findByTenantIdAndPersonCpidOrderByCreatedAtDesc(tenantId, personCpid);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentTaskEntity> enrollmentTasks(UUID enrollmentId) {
        return enrollmentTaskRepository.findByEnrollmentIdOrderByCreatedAtAsc(enrollmentId);
    }

    /** Result of an enrolment — includes the clinical-caution flag so callers can surface a care prompt. */
    public record EnrollmentResult(PlanEnrollmentEntity enrollment, boolean clinicalCaution) { }

    @Transactional
    public EnrollmentResult enroll(UUID tenantId, UUID planId, String personCpid, String enrolledBy) {
        WellnessPlanEntity plan = planRepository.findByPlanIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        if (!"ACTIVE".equals(plan.getStatus())) {
            throw new IllegalStateException("Plan is not open for enrolment");
        }
        enrollmentRepository.findByPlanIdAndPersonCpid(planId, personCpid).ifPresent(e -> {
            throw new IllegalStateException("Already enrolled");
        });

        PlanEnrollmentEntity enrollment = new PlanEnrollmentEntity();
        enrollment.setTenantId(tenantId);
        enrollment.setPlanId(planId);
        enrollment.setPersonCpid(personCpid);
        enrollment.setEnrolledBy(enrolledBy);
        enrollment = enrollmentRepository.save(enrollment);

        // Instantiate per-enrollment tasks from the plan template.
        for (PlanTaskEntity t : planTaskRepository.findByPlanIdOrderByStageAscSeqAsc(planId)) {
            EnrollmentTaskEntity et = new EnrollmentTaskEntity();
            et.setTenantId(tenantId);
            et.setEnrollmentId(enrollment.getEnrollmentId());
            et.setPlanTaskId(t.getPlanTaskId());
            et.setTitle(t.getTitle());
            enrollmentTaskRepository.save(et);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enrollment_id", enrollment.getEnrollmentId().toString());
        payload.put("plan_id", planId.toString());
        payload.put("plan_code", plan.getPlanCode());
        payload.put("person_cpid", personCpid);
        payload.put("clinical_caution", plan.isClinicalCaution());
        events.emit(tenantId, "WELLNESS_PLAN", enrollment.getEnrollmentId().toString(),
                "impilo.simba.wellness.plan.enrolled.v1", personCpid,
                "simba:plan-enroll:" + enrollment.getEnrollmentId(), payload);

        return new EnrollmentResult(enrollment, plan.isClinicalCaution());
    }

    @Transactional
    public PlanEnrollmentEntity completeTask(UUID tenantId, UUID enrollmentTaskId) {
        EnrollmentTaskEntity task = enrollmentTaskRepository
                .findByEnrollmentTaskIdAndTenantId(enrollmentTaskId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment task not found"));
        if (!"DONE".equals(task.getStatus())) {
            task.setStatus("DONE");
            task.setCompletedAt(OffsetDateTime.now());
            enrollmentTaskRepository.save(task);
        }

        PlanEnrollmentEntity enrollment = enrollmentRepository
                .findByEnrollmentIdAndTenantId(task.getEnrollmentId(), tenantId)
                .orElseThrow(() -> new IllegalStateException("Enrollment missing for task"));

        long total = enrollmentTaskRepository.countByEnrollmentId(enrollment.getEnrollmentId());
        long done = enrollmentTaskRepository.countByEnrollmentIdAndStatus(enrollment.getEnrollmentId(), "DONE");
        int pct = total == 0 ? 0 : (int) Math.round((done * 100.0) / total);
        enrollment.setProgressPct(pct);
        if (pct >= 100 && !"COMPLETED".equals(enrollment.getStatus())) {
            enrollment.setStatus("COMPLETED");
            enrollment.setCompletedAt(OffsetDateTime.now());
        }
        enrollment = enrollmentRepository.save(enrollment);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enrollment_id", enrollment.getEnrollmentId().toString());
        payload.put("enrollment_task_id", enrollmentTaskId.toString());
        payload.put("progress_pct", pct);
        payload.put("status", enrollment.getStatus());
        events.emit(tenantId, "WELLNESS_PLAN", enrollment.getEnrollmentId().toString(),
                "impilo.simba.wellness.plan.task.completed.v1", enrollment.getPersonCpid(),
                "simba:plan-task:" + enrollmentTaskId + ":done", payload);

        return enrollment;
    }
}
