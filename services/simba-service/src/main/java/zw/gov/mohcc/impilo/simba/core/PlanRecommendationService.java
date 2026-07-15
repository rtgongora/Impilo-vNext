package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.PlanEnrollmentEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.WellnessPlanEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.WellnessPlanRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recommends wellness plans by mapping the person's latest completed assessment (risk band + matched
 * risk questions) onto the active plan catalogue. Reuses the EXISTING wellness_plans / plan_enrollments
 * (no new plan table). {@link #assemble} delegates enrolment to {@link PlanService#enroll}, then links
 * preventive reminders and writes an ENROLMENT timeline row.
 */
@Service
public class PlanRecommendationService {

    private final WellnessPlanRepository planRepository;
    private final AssessmentService assessmentService;
    private final PlanService planService;
    private final PreventiveReminderService reminderService;
    private final TimelineService timeline;

    public PlanRecommendationService(WellnessPlanRepository planRepository,
                                     AssessmentService assessmentService,
                                     PlanService planService,
                                     PreventiveReminderService reminderService,
                                     TimelineService timeline) {
        this.planRepository = planRepository;
        this.assessmentService = assessmentService;
        this.planService = planService;
        this.reminderService = reminderService;
        this.timeline = timeline;
    }

    public record Recommendation(WellnessPlanEntity plan, String reason, int score) { }

    /**
     * Ranks active plans for a person by their latest completed assessment band. Deterministic:
     * category relevance + band weighting. No assessment → gentle default (activity/nutrition first).
     */
    @Transactional(readOnly = true)
    public List<Recommendation> recommend(UUID tenantId, String personCpid) {
        List<WellnessPlanEntity> active =
                planRepository.findByTenantIdAndStatusOrderByNameAsc(tenantId, "ACTIVE");
        AssessmentEntity latest = assessmentService.latestCompleted(tenantId, personCpid, "BASELINE_WELLNESS");
        String band = latest == null ? null : latest.getRiskBand();

        List<Recommendation> out = new ArrayList<>();
        for (WellnessPlanEntity plan : active) {
            int score = scorePlan(plan, band);
            if (score > 0) {
                out.add(new Recommendation(plan, reasonFor(plan, band), score));
            }
        }
        out.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return out;
    }

    static int scorePlan(WellnessPlanEntity plan, String band) {
        int score = 1; // every active plan is at least offerable
        String cat = plan.getCategory() == null ? "" : plan.getCategory();
        boolean elevated = band != null
                && (band.equals("HIGH") || band.equals("NEEDS_CLINICAL_REVIEW") || band.equals("URGENT_RED_FLAG"));
        boolean moderate = "MODERATE".equals(band);

        // Chronic-prevention/tobacco plans rise with elevated risk.
        if ((cat.equals("CHRONIC_PREVENTION") || cat.equals("TOBACCO")) && elevated) {
            score += 5;
        } else if ((cat.equals("CHRONIC_PREVENTION") || cat.equals("TOBACCO")) && moderate) {
            score += 3;
        }
        // Activity/nutrition/sleep are broadly beneficial; nudge for moderate.
        if (cat.equals("ACTIVITY") || cat.equals("NUTRITION") || cat.equals("SLEEP")) {
            score += moderate || elevated ? 2 : 3; // default (no/low risk) leans lifestyle
        }
        // Mental-wellbeing rises when a clinical-review band is present.
        if (cat.equals("MENTAL_WELLBEING") && "NEEDS_CLINICAL_REVIEW".equals(band)) {
            score += 4;
        }
        return score;
    }

    private static String reasonFor(WellnessPlanEntity plan, String band) {
        if (band == null) {
            return "A good starting point for building healthy habits.";
        }
        return switch (band) {
            case "LOW" -> "Keeps your healthy momentum going.";
            case "MODERATE" -> "Recommended to lower your wellness risk.";
            case "HIGH", "NEEDS_CLINICAL_REVIEW", "URGENT_RED_FLAG" ->
                    "Recommended given your recent assessment — supports your care follow-up.";
            default -> "Recommended for you.";
        };
    }

    /** Result of assembling a recommended plan into an active enrolment + reminders. */
    public record AssembledPlan(PlanEnrollmentEntity enrollment, boolean clinicalCaution,
                                int remindersCreated) { }

    /**
     * Enrols the person into the plan (reusing {@link PlanService#enroll}), then schedules a follow-up
     * reminder and writes an ENROLMENT timeline row. Idempotency/uniqueness is enforced by PlanService.
     */
    @Transactional
    public AssembledPlan assemble(UUID tenantId, UUID planId, String personCpid, String enrolledBy) {
        WellnessPlanEntity plan = planRepository.findByPlanIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        PlanService.EnrollmentResult result = planService.enroll(tenantId, planId, personCpid, enrolledBy);

        int reminders = 0;
        // A gentle habit reminder one week in supports adherence for any plan.
        reminderService.schedule(tenantId, personCpid, "HABIT",
                "Check in on your " + plan.getName(),
                "How is your wellness journey going? Log a check-in.",
                OffsetDateTime.now().plusDays(7), null, "PUSH",
                "plan:" + planId, enrolledBy);
        reminders++;
        // Clinical-caution plans schedule a screening-awareness reminder.
        if (plan.isClinicalCaution()) {
            reminderService.schedule(tenantId, personCpid, "SCREENING",
                    "Screening awareness for " + plan.getName(),
                    "This journey touches a health risk — consider a screening or provider chat.",
                    OffsetDateTime.now().plusDays(14), 12, "PUSH",
                    "plan:" + planId, enrolledBy);
            reminders++;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("plan_code", plan.getPlanCode());
        detail.put("clinical_caution", plan.isClinicalCaution());
        timeline.record(tenantId, personCpid, "ENROLMENT",
                "Enrolled in " + plan.getName(),
                "Your wellness journey has started.", "POSITIVE",
                "ENROLLMENT", result.enrollment().getEnrollmentId().toString(), detail);

        return new AssembledPlan(result.enrollment(), result.clinicalCaution(), reminders);
    }
}
