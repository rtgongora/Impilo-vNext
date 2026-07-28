package zw.gov.mohcc.impilo.clinical.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.persistence.entity.NationalPolicyParameterEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.NationalPolicyParameterRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reading a governed policy parameter, and the distinction that keeps a guess from becoming law.
 *
 * <p>The window semantics (open-ended accepted, closed-before-open refused) and the
 * ratified-implies-verified rule are mutation-proven against real Postgres in the V041 probe. Here:
 * that a seed is readable but not authoritative, that a missing version is an answer rather than an
 * error, and that "as of" means the date of the act.
 */
class NationalPolicyParameterServiceTest {

    private static final String CODE = "SRH_CONFIDENTIAL_FROM_GUARDIAN_AGE_YEARS";

    private NationalPolicyParameterRepository repo;
    private NationalPolicyParameterService service;

    @BeforeEach
    void setUp() {
        repo = mock(NationalPolicyParameterRepository.class);
        service = new NationalPolicyParameterService(repo);
    }

    private static NationalPolicyParameterEntity param(String value, String approval, String verification) {
        NationalPolicyParameterEntity p = new NationalPolicyParameterEntity();
        p.setParameterCode(CODE);
        p.setValueType("INTEGER");
        p.setValueText(value);
        p.setApprovalStatus(approval);
        p.setVerificationStatus(verification);
        p.setEffectiveStart(LocalDate.of(2026, 1, 1));
        return p;
    }

    @Test
    @DisplayName("the shipped seed is readable but is NOT ratified — a guess is not national policy")
    void seedIsReadableButNotRatified() {
        var seed = param("18",
                NationalPolicyParameterEntity.APPROVAL_ENGINEERING_SEED,
                NationalPolicyParameterEntity.VERIFICATION_UNVERIFIED);
        when(repo.findInForce(eq(CODE), any())).thenReturn(List.of(seed));

        var found = service.inForce(CODE, LocalDate.of(2026, 7, 28));
        assertThat(found).isPresent();
        assertThat(found.get().getValueText()).isEqualTo("18");
        // The whole point: a consumer can read it, and can tell it is not authority.
        assertThat(found.get().isRatified()).isFalse();
    }

    @Test
    @DisplayName("ratified requires BOTH statuses — verified-but-unratified is still not authority")
    void ratifiedRequiresBoth() {
        assertThat(param("16", NationalPolicyParameterEntity.APPROVAL_RATIFIED,
                NationalPolicyParameterEntity.VERIFICATION_VERIFIED).isRatified()).isTrue();
        assertThat(param("16", NationalPolicyParameterEntity.APPROVAL_ENGINEERING_SEED,
                NationalPolicyParameterEntity.VERIFICATION_VERIFIED).isRatified()).isFalse();
        assertThat(param("16", NationalPolicyParameterEntity.APPROVAL_WITHDRAWN,
                NationalPolicyParameterEntity.VERIFICATION_VERIFIED).isRatified()).isFalse();
    }

    @Test
    @DisplayName("no version covering the date is an empty answer, never a fabricated default")
    void noVersionInForceReturnsEmpty() {
        when(repo.findInForce(any(), any())).thenReturn(List.of());
        assertThat(service.inForce(CODE, LocalDate.of(2020, 1, 1))).isEmpty();
    }

    @Test
    @DisplayName("the newest window in force wins, so a superseding version takes effect on its date")
    void newestWindowWins() {
        var older = param("18", NationalPolicyParameterEntity.APPROVAL_ENGINEERING_SEED,
                NationalPolicyParameterEntity.VERIFICATION_UNVERIFIED);
        var newer = param("16", NationalPolicyParameterEntity.APPROVAL_ENGINEERING_SEED,
                NationalPolicyParameterEntity.VERIFICATION_UNVERIFIED);
        newer.setEffectiveStart(LocalDate.of(2027, 1, 1));
        // Repository orders effectiveStart DESC.
        when(repo.findInForce(eq(CODE), any())).thenReturn(List.of(newer, older));

        assertThat(service.inForce(CODE, LocalDate.of(2027, 6, 1)).orElseThrow().getValueText())
                .isEqualTo("16");
    }

    @Test
    @DisplayName("a blank code asks nothing of the database and answers empty")
    void blankCodeIsEmpty() {
        assertThat(service.inForce(null, LocalDate.now())).isEmpty();
        assertThat(service.inForce("  ", LocalDate.now())).isEmpty();
    }
}
