package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.PreconceptionPlanEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.PreconceptionPlanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Preconception care plans.
 *
 * <p>The one derived signal this service computes is timeliness, and it computes it honestly. A folic
 * acid start date after conception is recorded and reported as late rather than dropped or reinterpreted,
 * because "she was on folic acid" and "she started folic acid in time" are different clinical facts
 * and only the second prevents a neural tube defect.
 */
@Service
public class PreconceptionService {

    private static final Logger log = LoggerFactory.getLogger(PreconceptionService.class);

    /**
     * The high folic acid dose threshold. At or below this is the standard 0.4 mg; above it is the
     * 5 mg given for a previous neural tube defect, diabetes, obesity or an enzyme-inducing
     * anticonvulsant, and it needs its reason recorded.
     */
    private static final BigDecimal HIGH_DOSE_THRESHOLD_MG = new BigDecimal("1.0");

    private final PreconceptionPlanRepository plans;

    public PreconceptionService(PreconceptionPlanRepository plans) {
        this.plans = plans;
    }

    /**
     * Whether a preconception action happened in time to matter, once a pregnancy start date is known.
     *
     * @param folicAcidInTime null when it cannot be judged — no plan, no start date, or no pregnancy
     *                        date yet — because "unknown" and "too late" are different answers
     */
    public record TimelinessReview(
            Boolean folicAcidInTime,
            LocalDate folicAcidStartedOn,
            LocalDate pregnancyStartDate,
            String note) {
    }

    @Transactional(readOnly = true)
    public Optional<PreconceptionPlanEntity> active(UUID tenantId, String subjectCpid) {
        if (tenantId == null || subjectCpid == null || subjectCpid.isBlank()) {
            return Optional.empty();
        }
        return plans.findActive(tenantId, subjectCpid);
    }

    @Transactional(readOnly = true)
    public List<PreconceptionPlanEntity> history(UUID tenantId, String subjectCpid) {
        if (tenantId == null || subjectCpid == null || subjectCpid.isBlank()) {
            return List.of();
        }
        return plans.findBySubject(tenantId, subjectCpid);
    }

    @Transactional
    public PreconceptionPlanEntity open(PreconceptionPlanEntity plan) {
        if (plan.getClientOfflineId() != null && !plan.getClientOfflineId().isBlank()) {
            Optional<PreconceptionPlanEntity> replayed = plans
                    .findByTenantIdAndClientOfflineId(plan.getTenantId(), plan.getClientOfflineId());
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }
        // A category-4-strength invariant enforced above the database too: a teratogen found and no
        // action recorded is the outcome the whole review exists to prevent. The database refuses it;
        // the service refuses it with a message a caller can act on.
        requireTeratogenActionIfFound(plan);
        requireHighDoseReason(plan);

        if (plan.getPreconceptionPlanId() == null) {
            plan.setPreconceptionPlanId(UUID.randomUUID());
        }
        if (plan.getStatus() == null) {
            plan.setStatus(PreconceptionPlanEntity.STATUS_ACTIVE);
        }
        if (plan.getRecordedAt() == null) {
            plan.setRecordedAt(OffsetDateTime.now());
        }
        return plans.save(plan);
    }

    @Transactional
    public PreconceptionPlanEntity update(UUID tenantId, UUID planId, PreconceptionPlanEntity changes,
                                          String actor) {
        PreconceptionPlanEntity plan = plans.findById(planId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("No preconception plan " + planId));
        requireTeratogenActionIfFound(changes);
        requireHighDoseReason(changes);
        // Field-by-field copy is out of scope for this method's callers today; the guard is the point.
        plan.setUpdatedBy(actor);
        plan.setUpdatedAt(OffsetDateTime.now());
        return plans.save(plan);
    }

    /**
     * Was folic acid started before this pregnancy began?
     *
     * <p>Returns a null verdict rather than guessing when any input is missing. The neural tube
     * closes around day 28, so "in time" here means started on or before the pregnancy start date —
     * a deliberately strict reading, because a plan that calls a late start "in time" is the failure
     * this whole table was built with dated columns to avoid.
     */
    public TimelinessReview reviewTimeliness(PreconceptionPlanEntity plan, LocalDate pregnancyStartDate) {
        if (plan == null || plan.getFolicAcidStartedOn() == null || pregnancyStartDate == null) {
            return new TimelinessReview(null,
                    plan == null ? null : plan.getFolicAcidStartedOn(), pregnancyStartDate,
                    "Cannot judge timeliness: a folic acid start date and a pregnancy start date are "
                            + "both needed, and this is not a statement that it was late.");
        }
        boolean inTime = !plan.getFolicAcidStartedOn().isAfter(pregnancyStartDate);
        return new TimelinessReview(inTime, plan.getFolicAcidStartedOn(), pregnancyStartDate,
                inTime
                        ? "Folic acid was started before this pregnancy began."
                        : "Folic acid was started after this pregnancy began — after the neural tube "
                                + "had begun to close, so it did not serve its main purpose here.");
    }

    private static void requireTeratogenActionIfFound(PreconceptionPlanEntity plan) {
        if (PreconceptionPlanEntity.TERATOGEN_FOUND.equals(plan.getTeratogenicMedicationFound())
                && (plan.getTeratogenicMedicationAction() == null
                    || plan.getTeratogenicMedicationAction().isBlank())) {
            throw new IllegalArgumentException(
                    "A teratogenic medication was found but no action was recorded. Finding one and "
                            + "doing nothing is worse than not looking, because the record now says "
                            + "somebody looked.");
        }
    }

    private static void requireHighDoseReason(PreconceptionPlanEntity plan) {
        if (plan.getFolicAcidDoseMg() != null
                && plan.getFolicAcidDoseMg().compareTo(HIGH_DOSE_THRESHOLD_MG) > 0
                && (plan.getFolicAcidHighDoseReason() == null
                    || plan.getFolicAcidHighDoseReason().isBlank())) {
            throw new IllegalArgumentException(
                    "The high folic acid dose needs its reason recorded, or a considered 5 mg cannot "
                            + "be told from a typo.");
        }
    }
}
