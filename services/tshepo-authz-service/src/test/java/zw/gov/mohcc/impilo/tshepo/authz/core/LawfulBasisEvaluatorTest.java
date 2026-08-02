package zw.gov.mohcc.impilo.tshepo.authz.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.LawfulBasisType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consent is one lawful ground among several. These tests pin both halves of that: a non-consent
 * ground must not be refused for the absence of consent, and a ground this service cannot prove
 * must never be claimed.
 */
class LawfulBasisEvaluatorTest {

    private static AuthzInternalRequest request(String subjectId) {
        return new AuthzInternalRequest(
                UUID.randomUUID(), "actor-1", "PROVIDER", List.of("CLINICIAN"), "TREATMENT",
                null, UUID.randomUUID(), null, null, null,
                "GET", "/v1/patients/1", "GET:/v1/patients/1", "patients", "1",
                0, "sess-1", null, AuthenticationAssurance.none(),
                "prov-1", null, null, null, subjectId, "LOA3",
                "grant-7", null, DutyContext.absent());
    }

    @Test
    @DisplayName("public health is a lawful ground in its own right — not a consent question")
    void publicHealthIsStatutoryNotConsent() {
        var o = LawfulBasisEvaluator.evaluate(request("cpid-1"), PurposeOfUse.PUBLIC_HEALTH, false);
        assertThat(o.requiresExplicitConsent()).isFalse();
        assertThat(o.decision().basisType()).isEqualTo(LawfulBasisType.PUBLIC_HEALTH_AUTHORITY);
        assertThat(o.decision().permitted()).isTrue();
    }

    @Test
    @DisplayName("regulatory duty is statutory authority")
    void regulatoryDutyIsStatutory() {
        var o = LawfulBasisEvaluator.evaluate(request("cpid-1"), PurposeOfUse.REGULATORY_DUTY, false);
        assertThat(o.requiresExplicitConsent()).isFalse();
        assertThat(o.decision().basisType()).isEqualTo(LawfulBasisType.STATUTORY_AUTHORITY);
    }

    @Test
    @DisplayName("BREAK_GLASS is a basis only when the upstream control VERIFIED it")
    void breakGlassRequiresVerification() {
        // An unverified break-glass is a client-asserted purpose header. Treating it as a lawful
        // ground would make the break-glass control theatre.
        var unverified = LawfulBasisEvaluator.evaluate(request("cpid-1"), PurposeOfUse.BREAK_GLASS, false);
        assertThat(unverified.requiresExplicitConsent())
                .as("an unverified break-glass claim must not establish a basis")
                .isTrue();

        var verified = LawfulBasisEvaluator.evaluate(request("cpid-1"), PurposeOfUse.BREAK_GLASS, true);
        assertThat(verified.requiresExplicitConsent()).isFalse();
        assertThat(verified.decision().basisType()).isEqualTo(LawfulBasisType.EMERGENCY_BREAK_GLASS);
        assertThat(verified.decision().basisReference())
                .as("the grant that justified it must be carried, or the audit cannot be reconstructed")
                .isEqualTo("grant-7");
    }

    @Test
    @DisplayName("TREATMENT still requires consent — direct care is never inferred from a purpose header")
    void treatmentDoesNotClaimDirectCare() {
        var o = LawfulBasisEvaluator.evaluate(request("cpid-1"), PurposeOfUse.TREATMENT, false);
        assertThat(o.requiresExplicitConsent()).isTrue();
        assertThat(o.decision()).isNull();
    }

    @Test
    @DisplayName("DIRECT_CARE_RELATIONSHIP is never returned — this service cannot prove one")
    void directCareIsNeverClaimed() {
        // Claiming it on purpose=TREATMENT would let any caller self-declare a treating
        // relationship by setting a header: the confused deputy this programme exists to close.
        for (PurposeOfUse p : PurposeOfUse.values()) {
            for (boolean verified : new boolean[]{true, false}) {
                var o = LawfulBasisEvaluator.evaluate(request("cpid-1"), p, verified);
                if (o.decision() != null) {
                    assertThat(o.decision().basisType())
                            .as("purpose %s must not claim direct care", p)
                            .isNotEqualTo(LawfulBasisType.DIRECT_CARE_RELATIONSHIP);
                }
            }
        }
    }

    @Test
    @DisplayName("the contract itself refuses to model consent as a lawful-basis decision")
    void consentCannotBeModelledAsABasis() {
        assertThatThrownBy(() -> zw.gov.mohcc.impilo.tshepo.contracts.v1.LawfulBasisDecision
                .permit(LawfulBasisType.EXPLICIT_CONSENT, null, "cpid-1", "TREATMENT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a null purpose falls back to requiring consent, never to a free pass")
    void nullPurposeRequiresConsent() {
        assertThat(LawfulBasisEvaluator.evaluate(request("cpid-1"), null, true)
                .requiresExplicitConsent()).isTrue();
    }

    @Test
    @DisplayName("metric labels stay inside a closed vocabulary")
    void metricLabelIsBounded() {
        assertThat(LawfulBasisEvaluator.evaluate(request("cpid-1"), PurposeOfUse.TREATMENT, false)
                .metricLabel()).isEqualTo("EXPLICIT_CONSENT");
        assertThat(LawfulBasisEvaluator.evaluate(request("cpid-1"), PurposeOfUse.PUBLIC_HEALTH, false)
                .metricLabel()).isEqualTo("PUBLIC_HEALTH_AUTHORITY");
    }

    @Test
    @DisplayName("mode parsing refuses a typo rather than silently disabling the control")
    void modeTypoIsRefused() {
        assertThat(LawfulBasisEvaluator.Mode.parse(null)).isEqualTo(LawfulBasisEvaluator.Mode.SHADOW);
        assertThat(LawfulBasisEvaluator.Mode.parse("enforce")).isEqualTo(LawfulBasisEvaluator.Mode.ENFORCE);
        assertThatThrownBy(() -> LawfulBasisEvaluator.Mode.parse("ENFORCED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected OFF, SHADOW or ENFORCE");
    }
}
