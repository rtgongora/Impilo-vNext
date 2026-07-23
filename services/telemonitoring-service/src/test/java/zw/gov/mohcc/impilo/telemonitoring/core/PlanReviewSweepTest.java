package zw.gov.mohcc.impilo.telemonitoring.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.MonitoringPlanService.CreatePlanCommand;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OF-B21 review-cadence timer groundwork: the sweep emits
 * {@code telemonitoring.plan.review_due.v1} when {@code now > last_review + cadence},
 * signals once per overdue period, and re-arms when a review is recorded.
 */
@SpringBootTest
@ActiveProfiles("test")
class PlanReviewSweepTest {

    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private EventOutboxRepository outboxRepository;

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void seedProgramme() {
        if (programmeRepository.findByCode("HYPERTENSION").isEmpty()) {
            MonitoringProgrammeEntity p = new MonitoringProgrammeEntity();
            p.setCode("HYPERTENSION");
            p.setName("Hypertension");
            p.setDefaultThresholds("{\"systolic_bp\":{\"greenMax\":140}}");
            programmeRepository.save(p);
        }
    }

    private MonitoringPlanEntity activePlan(String cadence) {
        MonitoringPlanEntity plan = planService.createDraft(new CreatePlanCommand(
                tenant, "CPID-" + UUID.randomUUID(), "HYPERTENSION", null,
                "Essential hypertension", "HOME_SELF_MONITORING", cadence, null, null,
                null, null, "clinician-a", null, "GRANTED"), true);
        return planService.approve(plan.getId(), "clinician-b");
    }

    @Test
    void overdueReviewEmitsReviewDueOncePerPeriod() {
        MonitoringPlanEntity plan = activePlan("DAILY");
        OffsetDateTime overdue = OffsetDateTime.now().plusDays(2);

        planService.sweepReviewsDue(overdue);
        assertThat(reviewDueCount(plan)).isEqualTo(1);
        EventOutboxEntity event = lastReviewDue(plan);
        assertThat(event.getPayloadJson()).contains("\"reviewCadence\":\"DAILY\"");
        assertThat(event.getPayloadJson()).contains("dueSince");

        // Second sweep in the same overdue period: de-duplicated, no spam.
        planService.sweepReviewsDue(overdue.plusHours(1));
        assertThat(reviewDueCount(plan)).isEqualTo(1);
    }

    @Test
    void recordedReviewReArmsTheTimer() {
        MonitoringPlanEntity plan = activePlan("DAILY");
        planService.sweepReviewsDue(OffsetDateTime.now().plusDays(2));
        assertThat(reviewDueCount(plan)).isEqualTo(1);

        MonitoringPlanEntity reviewed = planService.recordReview(plan.getId(), "clinician-b");
        assertThat(reviewed.getLastReviewAt()).isNotNull();
        assertThat(reviewed.getReviewDueNotifiedAt()).isNull();
        assertThat(events(plan)).contains("telemonitoring.plan.review_recorded.v1");

        // Not yet due against the NEW last_review …
        planService.sweepReviewsDue(reviewed.getLastReviewAt().plusHours(12));
        assertThat(reviewDueCount(plan)).isEqualTo(1);

        // … but overdue again one cadence past it.
        planService.sweepReviewsDue(reviewed.getLastReviewAt().plus(Duration.ofDays(1)).plusMinutes(1));
        assertThat(reviewDueCount(plan)).isEqualTo(2);
    }

    @Test
    void notYetDuePlansAreLeftAlone() {
        MonitoringPlanEntity plan = activePlan("WEEKLY");
        planService.sweepReviewsDue(OffsetDateTime.now().plusDays(3));
        assertThat(reviewDueCount(plan)).isZero();
    }

    @Test
    void suspendedPlansAreNotSwept() {
        MonitoringPlanEntity plan = activePlan("DAILY");
        planService.suspend(plan.getId(), "Admitted to hospital", "clinician-b");
        planService.sweepReviewsDue(OffsetDateTime.now().plusDays(30));
        assertThat(reviewDueCount(plan)).isZero();
    }

    @Test
    void unparseableCadenceIsSkippedNeverGuessed() {
        MonitoringPlanEntity plan = activePlan("WHENEVER_CONVENIENT");
        planService.sweepReviewsDue(OffsetDateTime.now().plusDays(365));
        assertThat(reviewDueCount(plan)).isZero();
    }

    @Test
    void cadenceVocabularyParses() {
        assertThat(MonitoringPlanService.parseCadence("DAILY")).isEqualTo(Duration.ofDays(1));
        assertThat(MonitoringPlanService.parseCadence("weekly")).isEqualTo(Duration.ofDays(7));
        assertThat(MonitoringPlanService.parseCadence("FORTNIGHTLY")).isEqualTo(Duration.ofDays(14));
        assertThat(MonitoringPlanService.parseCadence("MONTHLY")).isEqualTo(Duration.ofDays(30));
        assertThat(MonitoringPlanService.parseCadence("QUARTERLY")).isEqualTo(Duration.ofDays(90));
        assertThat(MonitoringPlanService.parseCadence("P3D")).isEqualTo(Duration.ofDays(3));
        assertThat(MonitoringPlanService.parseCadence("nonsense")).isNull();
        assertThat(MonitoringPlanService.parseCadence(null)).isNull();
        assertThat(MonitoringPlanService.parseCadence("  ")).isNull();
    }

    private long reviewDueCount(MonitoringPlanEntity plan) {
        return outboxRepository.findByAggregateIdOrderByIdAsc(plan.getId().toString()).stream()
                .filter(e -> "telemonitoring.plan.review_due.v1".equals(e.getEventType()))
                .count();
    }

    private EventOutboxEntity lastReviewDue(MonitoringPlanEntity plan) {
        return outboxRepository.findByAggregateIdOrderByIdAsc(plan.getId().toString()).stream()
                .filter(e -> "telemonitoring.plan.review_due.v1".equals(e.getEventType()))
                .reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("Expected a review_due event"));
    }

    private java.util.List<String> events(MonitoringPlanEntity plan) {
        return outboxRepository.findByAggregateIdOrderByIdAsc(plan.getId().toString()).stream()
                .map(EventOutboxEntity::getEventType)
                .toList();
    }
}
