package zw.gov.mohcc.impilo.tshepo.authz.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.Obligations;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins obligation composition rules that must hold regardless of PolicyEngine wiring —
 * including mandatory-reporting paths that must not inherit SRH confidential grants.
 */
class VisibilityObligationComposerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static AuthzInternalRequest request(PurposeOfUse purpose) {
        return new AuthzInternalRequest(
                TENANT, "actor-1", "PROVIDER", List.of("CLINICIAN"), purpose.name(),
                "device", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, "GET", "/v1/pct/confidential/reports/1", "GET:/v1/pct/confidential/reports/1",
                "confidential-reports", "1",
                3, "session", null,
                null, null, null, null, "subject-1", "LOA3",
                null, null, zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext.absent()
        );
    }

    private static PolicyRuleEntity ruleWithSrhCategories() {
        PolicyRuleEntity rule = new PolicyRuleEntity();
        rule.setName("confidential-lane-read-clinician");
        rule.setConditions("""
                {"visibility":{"confidentialCategories":["SEXUAL_REPRODUCTIVE_HEALTH","HIV"]}}""");
        return rule;
    }

    @Test
    @DisplayName("REGULATORY_DUTY revokes confidential categories even when the rule overlay grants SRH")
    void regulatoryDuty_neverInheritsConfidentialCategories() {
        Obligations o = VisibilityObligationComposer.compose(
                request(PurposeOfUse.REGULATORY_DUTY),
                PurposeOfUse.REGULATORY_DUTY,
                10,
                ruleWithSrhCategories(),
                Optional.empty(),
                new ObjectMapper(),
                List.of("SEXUAL_REPRODUCTIVE_HEALTH"));

        assertFalse(o.visibilityProfile().allowsAnyConfidentialCategory(),
                "mandatory reporting must not inherit SRH confidential grants (PO 2026-07-31)");
    }

    @Test
    @DisplayName("PUBLIC_HEALTH revokes confidential categories even when the rule overlay grants SRH")
    void publicHealth_neverInheritsConfidentialCategories() {
        Obligations o = VisibilityObligationComposer.compose(
                request(PurposeOfUse.PUBLIC_HEALTH),
                PurposeOfUse.PUBLIC_HEALTH,
                10,
                ruleWithSrhCategories(),
                Optional.empty(),
                new ObjectMapper(),
                List.of("SEXUAL_REPRODUCTIVE_HEALTH", "HIV"));

        assertFalse(o.visibilityProfile().allowsAnyConfidentialCategory(),
                "public-health reporting must not inherit SRH confidential grants (PO 2026-07-31)");
    }

    @Test
    @DisplayName("TREATMENT retains governed confidential categories when granted")
    void treatment_retainsGrantedCategories() {
        Obligations o = VisibilityObligationComposer.compose(
                request(PurposeOfUse.TREATMENT),
                PurposeOfUse.TREATMENT,
                10,
                ruleWithSrhCategories(),
                Optional.empty(),
                new ObjectMapper(),
                List.of("SEXUAL_REPRODUCTIVE_HEALTH"));

        assertTrue(o.visibilityProfile().allowsAnyConfidentialCategory());
    }
}
