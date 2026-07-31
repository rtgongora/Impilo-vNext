package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.persistence.entity.MpdsrReadinessTaskEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.MpdsrReviewEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.MpdsrReviewRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MpdsrReviewServiceTest {

    @Autowired private MpdsrReviewService mpdsrReviewService;
    @Autowired private MpdsrReviewRepository reviewRepository;

    @Test
    @Transactional
    void openFromDeathEvent_isIdempotentByDeathCaseId() {
        UUID tenant = UUID.randomUUID();
        UUID deathCase = UUID.randomUUID();
        UUID facility = UUID.randomUUID();

        MpdsrReviewService.DeathEventIntake intake = new MpdsrReviewService.DeathEventIntake(
                deathCase, facility, null, "MATERNAL", null, null, "FACILITY");

        MpdsrReviewEntity first = mpdsrReviewService.openFromDeathEvent(tenant, intake, "qi-lead-1");
        MpdsrReviewEntity second = mpdsrReviewService.openFromDeathEvent(tenant, intake, "qi-lead-2");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(reviewRepository.findByTenantIdAndDeathCaseId(tenant, deathCase)).isPresent();
        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional
    void raiseFacilityReadinessTask_emitsSanitizedPayloadOnly() {
        UUID tenant = UUID.randomUUID();
        UUID deathCase = UUID.randomUUID();
        UUID facility = UUID.randomUUID();

        MpdsrReviewEntity review = mpdsrReviewService.openFromDeathEvent(tenant,
                new MpdsrReviewService.DeathEventIntake(
                        deathCase, facility, null, "MATERNAL", null, null, "FACILITY"),
                "qi-lead");

        MpdsrReadinessTaskEntity task = mpdsrReviewService.raiseFacilityReadinessTask(
                tenant, review.getId(), OffsetDateTime.parse("2026-08-01T09:00:00Z"));

        assertThat(task.getPayloadJson()).doesNotContain("reviewId", "deathCaseId", "narrative", "findings");
        assertThat(task.getPayloadJson()).contains("FACILITY_READINESS_REASSESSMENT");
        assertThat(task.getPayloadJson()).contains(facility.toString());
    }
}
