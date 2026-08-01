package zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.ConsentDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.RecoveryAuthenticationState;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeOutcome;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checkpoint-2 closure conformance for compatibility adapters: no adapter may increase
 * AAL, authority, scope, context or consent; recovery stays constrained in both
 * directions; unrepresentable canonical outcomes fail closed; live legacy paths get a
 * total, deny-by-default reverse mapping instead of an unhandled exception.
 */
class TrustAdapterConformanceTest {

    private static final Set<TrustChallengeDecision> LEGACY_REPRESENTABLE = EnumSet.of(
            TrustChallengeDecision.ALLOW,
            TrustChallengeDecision.DENY,
            TrustChallengeDecision.STEP_UP_REQUIRED);

    // ---- authentication assurance: no AAL increase, recovery stays constrained ----

    @Test
    @DisplayName("legacy->canonical never increases AAL for any legacy input")
    void toCanonicalNeverIncreasesAal() {
        for (int aal = 0; aal <= 3; aal++) {
            for (List<String> amr : List.of(
                    List.<String>of(),
                    List.of("pwd"),
                    List.of("pwd", "totp"),
                    List.of("recovery-code"),
                    List.of("auth-recovery-authn-code-form"))) {
                var legacy = new zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance(
                        aal, amr, Instant.parse("2026-08-01T08:00:00Z"), null, false, "s1", "f1");
                AuthenticationAssurance canonical =
                        LegacyAuthenticationAssuranceAdapter.toCanonical(legacy);
                assertThat(canonical.aal())
                        .as("aal must not increase (legacy=%s amr=%s)", aal, amr)
                        .isLessThanOrEqualTo(aal);
                boolean recoveryAmr = amr.stream().anyMatch(m -> m.contains("recovery"));
                if (recoveryAmr) {
                    assertThat(canonical.recoveryState())
                            .isEqualTo(RecoveryAuthenticationState.CONSTRAINED_RECOVERY);
                    assertThat(canonical.grantsOrdinaryWorkforceAal2())
                            .as("recovery AMR must never yield ordinary workforce AAL2")
                            .isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("canonical->legacy demotes constrained recovery below AAL2 (never broadens)")
    void toLegacyDemotesConstrainedRecovery() {
        AuthenticationAssurance constrained = AuthenticationAssurance.of(
                2, List.of("recovery-code"), Instant.parse("2026-08-01T08:30:00Z"), null,
                false, "sess-r", null, RecoveryAuthenticationState.CONSTRAINED_RECOVERY);
        var legacy = LegacyAuthenticationAssuranceAdapter.toLegacy(constrained);
        assertThat(legacy.aal()).isLessThanOrEqualTo(1);
        assertThat(legacy.methods()).contains("recovery-code");
    }

    @Test
    @DisplayName("recovery remains constrained across a legacy->canonical->legacy round trip")
    void recoveryRoundTripStaysConstrained() {
        var legacyIn = new zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance(
                2, List.of("recovery-code"), Instant.parse("2026-08-01T08:30:00Z"),
                null, false, "sess-r", null);
        AuthenticationAssurance canonical = LegacyAuthenticationAssuranceAdapter.toCanonical(legacyIn);
        assertThat(canonical.isConstrainedRecovery()).isTrue();
        var legacyOut = LegacyAuthenticationAssuranceAdapter.toLegacy(canonical);
        assertThat(legacyOut.aal())
                .as("round trip must not restore ordinary AAL2 for recovery sessions")
                .isLessThanOrEqualTo(1);
        AuthenticationAssurance again = LegacyAuthenticationAssuranceAdapter.toCanonical(legacyOut);
        assertThat(again.isConstrainedRecovery()).isTrue();
        assertThat(again.grantsOrdinaryWorkforceAal2()).isFalse();
    }

    @Test
    @DisplayName("null legacy assurance maps to no assurance, never a privileged default")
    void nullLegacyAssuranceMapsToNone() {
        AuthenticationAssurance canonical = LegacyAuthenticationAssuranceAdapter.toCanonical(null);
        assertThat(canonical.aal()).isZero();
        assertThat(canonical.grantsOrdinaryWorkforceAal2()).isFalse();
    }

    // ---- challenge outcome: fail closed, no privilege by default ----

    @Test
    @DisplayName("null or verdict-less legacy responses map to canonical DENY, never ALLOW")
    void unknownLegacyVerdictFailsClosed() {
        TrustChallengeOutcome fromNull =
                AuthzResponseChallengeAdapter.toCanonical(null, "dec-1", "p1");
        assertThat(fromNull.decision()).isEqualTo(TrustChallengeDecision.DENY);

        AuthzResponse noVerdict = new AuthzResponse(null, null, null, null, null, 0, null, null);
        TrustChallengeOutcome fromNoVerdict =
                AuthzResponseChallengeAdapter.toCanonical(noVerdict, "dec-2", "p1");
        assertThat(fromNoVerdict.decision()).isEqualTo(TrustChallengeDecision.DENY);
    }

    @Test
    @DisplayName("legacy step-up mapping preserves the method list without widening it")
    void stepUpMethodsNotWidened() {
        AuthzResponse legacy = AuthzResponse.stepUp(List.of("totp"), 10);
        TrustChallengeOutcome canonical =
                AuthzResponseChallengeAdapter.toCanonical(legacy, "dec-3", "p1");
        assertThat(canonical.decision()).isEqualTo(TrustChallengeDecision.STEP_UP_REQUIRED);
        assertThat(canonical.allowedAuthenticationMethods()).containsExactly("totp");
        assertThat(canonical.requiredAssurance()).isEqualTo(2);
    }

    @Test
    @DisplayName("every unrepresentable canonical outcome fails closed via toLegacy (throws)")
    void unrepresentableOutcomesThrowInStrictMapping() {
        for (TrustChallengeDecision decision : TrustChallengeDecision.values()) {
            if (LEGACY_REPRESENTABLE.contains(decision)) {
                continue;
            }
            TrustChallengeOutcome canonical = TrustChallengeOutcome.of(
                    decision, "R", "trust.msg", null, null, List.of(), List.of(),
                    null, null, null, "dec-4", "p1", null, null);
            assertThatThrownBy(() -> AuthzResponseChallengeAdapter.toLegacy(canonical))
                    .as("decision %s must not silently map onto the legacy wire", decision)
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    @DisplayName("toLegacySafe is total: every canonical decision maps without throwing, deny by default")
    void toLegacySafeNeverThrowsAndNeverAllowsByDefault() {
        for (TrustChallengeDecision decision : TrustChallengeDecision.values()) {
            TrustChallengeOutcome canonical = TrustChallengeOutcome.of(
                    decision, "R", "trust.msg", null, null, List.of(), List.of(),
                    null, null, null, "dec-5", "p1", null, null);
            AuthzResponse legacy = AuthzResponseChallengeAdapter.toLegacySafe(canonical);
            assertThat(legacy).isNotNull();
            if (decision == TrustChallengeDecision.ALLOW) {
                assertThat(legacy.verdict()).isEqualTo(Verdict.ALLOW);
            } else if (decision == TrustChallengeDecision.STEP_UP_REQUIRED) {
                assertThat(legacy.verdict()).isEqualTo(Verdict.STEP_UP_REQUIRED);
            } else {
                assertThat(legacy.verdict())
                        .as("non-representable %s must become legacy DENY (403 path), not 500", decision)
                        .isEqualTo(Verdict.DENY);
            }
            if (!LEGACY_REPRESENTABLE.contains(decision)) {
                assertThat(legacy.errorCode()).isEqualTo("UNREPRESENTABLE_" + decision.name());
                assertThat(legacy.errorMessage()).doesNotContain("eyJ", "Bearer");
            }
        }
    }

    // ---- consent: adapters must not widen or invent consent ----

    @Test
    @DisplayName("null legacy consent maps to a canonical deny with no scopes")
    void nullLegacyConsentDenies() {
        ConsentDecision canonical =
                LegacyConsentDecisionAdapter.toCanonical(null, "cpid-1", "TREATMENT");
        assertThat(canonical.permitted()).isFalse();
        assertThat(canonical.allowedScopes()).isEmpty();
    }

    @Test
    @DisplayName("consent scope sets survive round trip without widening")
    void consentScopesNotWidened() {
        var legacyIn = zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision.permit(
                "consent-1", List.of("observation:read"));
        ConsentDecision canonical =
                LegacyConsentDecisionAdapter.toCanonical(legacyIn, "cpid-1", "TREATMENT");
        assertThat(canonical.allowedScopes()).containsExactly("observation:read");
        var legacyOut = LegacyConsentDecisionAdapter.toLegacy(canonical);
        assertThat(legacyOut.permitted()).isEqualTo(legacyIn.permitted());
        assertThat(legacyOut.allowedScopes()).containsExactlyElementsOf(legacyIn.allowedScopes());
    }

    @Test
    @DisplayName("denied legacy consent can never round-trip into a permit")
    void deniedConsentStaysDenied() {
        var legacyDeny = zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision.deny("NO_CONSENT");
        ConsentDecision canonical =
                LegacyConsentDecisionAdapter.toCanonical(legacyDeny, "cpid-2", "TREATMENT");
        assertThat(canonical.permitted()).isFalse();
        assertThat(LegacyConsentDecisionAdapter.toLegacy(canonical).permitted()).isFalse();
    }
}
