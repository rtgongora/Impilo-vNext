package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.PreconceptionPlanEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.PreconceptionPlanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Preconception care.
 *
 * <p>The behaviour under test is the timeliness of the one intervention where timing is everything:
 * folic acid, which only prevents a neural tube defect if it was taken before the tube closed. A plan
 * that calls a late start "in time" is the exact failure the dated columns exist to prevent.
 */
class PreconceptionServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private PreconceptionPlanRepository plans;
    private PreconceptionService service;

    @BeforeEach
    void setUp() {
        plans = mock(PreconceptionPlanRepository.class);
        service = new PreconceptionService(plans);
        when(plans.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(plans.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());
    }

    private static PreconceptionPlanEntity plan() {
        PreconceptionPlanEntity p = new PreconceptionPlanEntity();
        p.setTenantId(TENANT);
        p.setSubjectCpid("CPID-P");
        p.setStatus("ACTIVE");
        p.setOpenedOn(LocalDate.of(2026, 1, 1));
        p.setRecordedBy("test");
        return p;
    }

    @Test
    @DisplayName("folic acid started before the pregnancy is in time")
    void folicAcidBeforePregnancyIsInTime() {
        var p = plan();
        p.setFolicAcidStartedOn(LocalDate.of(2025, 11, 1));

        var review = service.reviewTimeliness(p, LocalDate.of(2026, 1, 1));

        assertThat(review.folicAcidInTime()).isTrue();
        assertThat(review.note()).contains("before this pregnancy began");
    }

    @Test
    @DisplayName("folic acid started after conception is reported late, not dropped")
    void folicAcidAfterConceptionIsLate() {
        var p = plan();
        p.setFolicAcidStartedOn(LocalDate.of(2026, 2, 15));

        var review = service.reviewTimeliness(p, LocalDate.of(2026, 1, 1));

        // "She was on folic acid" and "she started in time" are different facts, and the neural
        // tube had already begun to close. The plan reports the second honestly.
        assertThat(review.folicAcidInTime()).isFalse();
        assertThat(review.note()).contains("did not serve its main purpose");
    }

    @Test
    @DisplayName("timeliness is null, not false, when a date is missing")
    void timelinessIsUnknownRatherThanGuessed() {
        var noStart = service.reviewTimeliness(plan(), LocalDate.of(2026, 1, 1));
        assertThat(noStart.folicAcidInTime()).isNull();
        assertThat(noStart.note()).contains("not a statement that it was late");

        var p = plan();
        p.setFolicAcidStartedOn(LocalDate.of(2025, 12, 1));
        var noPregnancyDate = service.reviewTimeliness(p, null);
        assertThat(noPregnancyDate.folicAcidInTime()).isNull();
    }

    @Test
    @DisplayName("a teratogen found with no action is refused")
    void teratogenFoundNeedsAction() {
        var p = plan();
        p.setTeratogenicMedicationReviewOn(LocalDate.of(2026, 1, 1));
        p.setTeratogenicMedicationFound(PreconceptionPlanEntity.TERATOGEN_FOUND);

        assertThatThrownBy(() -> service.open(p))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worse than not looking");
    }

    @Test
    @DisplayName("a teratogen found WITH an action is accepted")
    void teratogenFoundWithActionIsAccepted() {
        var p = plan();
        p.setTeratogenicMedicationReviewOn(LocalDate.of(2026, 1, 1));
        p.setTeratogenicMedicationFound(PreconceptionPlanEntity.TERATOGEN_FOUND);
        p.setTeratogenicMedicationDetail("Sodium valproate for epilepsy");
        p.setTeratogenicMedicationAction("Referred to neurology to switch before conception");

        assertThat(service.open(p).getPreconceptionPlanId()).isNotNull();
    }

    @Test
    @DisplayName("the 5 mg folate dose needs its reason")
    void highDoseFolateNeedsReason() {
        var p = plan();
        p.setFolicAcidStartedOn(LocalDate.of(2026, 1, 1));
        p.setFolicAcidDoseMg(new BigDecimal("5.0"));

        assertThatThrownBy(() -> service.open(p))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be told from a typo");
    }

    @Test
    @DisplayName("the standard 0.4 mg dose needs no reason")
    void standardDoseNeedsNoReason() {
        var p = plan();
        p.setFolicAcidStartedOn(LocalDate.of(2026, 1, 1));
        p.setFolicAcidDoseMg(new BigDecimal("0.4"));

        assertThat(service.open(p).getPreconceptionPlanId()).isNotNull();
    }

    @Test
    @DisplayName("a replayed offline plan returns the existing one")
    void offlineReplayIsIdempotent() {
        var existing = plan();
        existing.setPreconceptionPlanId(UUID.randomUUID());
        existing.setClientOfflineId("dev-1");
        when(plans.findByTenantIdAndClientOfflineId(TENANT, "dev-1"))
                .thenReturn(Optional.of(existing));

        var incoming = plan();
        incoming.setClientOfflineId("dev-1");

        assertThat(service.open(incoming).getPreconceptionPlanId())
                .isEqualTo(existing.getPreconceptionPlanId());
    }
}
