package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shipped state of the governed confidentiality pack after W14-D preview flip.
 */
class ConfidentialityPolicyPackTest {

    private ConfidentialityPolicyPack pack;

    @BeforeEach
    void setUp() {
        pack = new ConfidentialityPolicyPack(new ObjectMapper());
        pack.load();
    }

    @Test
    @DisplayName("the pack as shipped is RATIFIED and EFFECTIVE after W14-D preview flip")
    void shippedPackIsRatifiedForPreview() {
        assertTrue(pack.isEffective(),
                "PO preview flip 2026-07-31 ratified the pack — if this fails, confirm governance "
                        + "before editing the test.");
        assertEquals("RATIFIED", pack.approvalStatus());
        assertEquals(6, pack.grantableCategories().size());
    }

    @Test
    @DisplayName("a named category is grantable when the pack is ratified")
    void namedCategoriesAreGrantableWhenRatified() {
        for (String category : List.of("HIV", "SEXUAL_REPRODUCTIVE_HEALTH", "MENTAL_HEALTH",
                "SAFEGUARDING", "SUBSTANCE_USE", "GENDER_BASED_VIOLENCE")) {
            assertTrue(pack.isGrantable(category), category + " must be grantable when ratified");
        }
        assertEquals(2, pack.retainGrantable(List.of("HIV", "SAFEGUARDING")).size());
    }

    @Test
    @DisplayName("the pack loads with PO preview version")
    void ratifiedPackLoads() {
        assertEquals("impilo.confidentiality.adolescent", pack.packId());
        assertEquals("po-preview-2026-07-31", pack.version());
        assertEquals("EFFECTIVE", pack.stateLabel());
    }

    @Test
    @DisplayName("the whole-set grant survives ratification — it belongs to the mechanism, not the content")
    void wholeSetGrantSurvivesRatification() {
        assertTrue(pack.isGrantable("*"),
                "self-access and the audited emergency waiver are properties of the mechanism. If "
                        + "the inert pack revoked them, emergency care would break before the "
                        + "content was ever ratified.");
    }

    @Test
    @DisplayName("SRH treatment rules carry PO 2026-07-31 ages — contraception 18, STI/TOP CASE_BY_CASE")
    void srhTreatmentRulesReflectPoRuling() {
        ConfidentialityPolicyPack.AgeRule contraception = pack.ageRules().stream()
                .filter(r -> "SEXUAL_REPRODUCTIVE_HEALTH".equals(r.category())
                        && "CONTRACEPTION".equals(r.treatment()))
                .findFirst()
                .orElseThrow();
        assertEquals(18, contraception.confidentialFromGuardianAgeYears());
        assertEquals("UNVERIFIED", contraception.verificationStatus());
        assertFalse(contraception.usable());

        for (String treatment : List.of("STI", "TOP")) {
            ConfidentialityPolicyPack.AgeRule rule = pack.ageRules().stream()
                    .filter(r -> "SEXUAL_REPRODUCTIVE_HEALTH".equals(r.category())
                            && treatment.equals(r.treatment()))
                    .findFirst()
                    .orElseThrow();
            assertNull(rule.confidentialFromGuardianAgeYears(), treatment + " age must stay unset");
            assertEquals("CASE_BY_CASE", rule.assessmentMode());
            assertEquals("UNVERIFIED", rule.verificationStatus());
            assertFalse(rule.usable());
        }
    }

    @Test
    @DisplayName("non-SRH age thresholds ship unset and unverified")
    void nonSrhAgeThresholdsAreUnsetAndUnverified() {
        for (String category : List.of("HIV", "MENTAL_HEALTH",
                "SAFEGUARDING", "SUBSTANCE_USE", "GENDER_BASED_VIOLENCE")) {
            ConfidentialityPolicyPack.AgeRule rule = pack.ageRuleFor(category);
            assertNotNull(rule, "the pack must declare an age rule slot for " + category
                    + " so the open question is visible rather than forgotten");
            assertNull(rule.confidentialFromGuardianAgeYears(),
                    category + " must ship with no threshold. A guessed age that looks "
                            + "authoritative is worse than an obviously missing one.");
            assertEquals("UNVERIFIED", rule.verificationStatus());
            assertFalse(rule.usable(), category + " age rule must not be usable");
            assertNotNull(rule.legalBasisToVerify(),
                    category + " must name the instrument that would settle the question");
        }
    }

    @Test
    @DisplayName("an unknown category has no age rule rather than a permissive default")
    void unknownCategoryHasNoRule() {
        assertNull(pack.ageRuleFor("NOT_A_CATEGORY"));
        assertNull(pack.ageRuleFor(null));
        assertFalse(pack.isGrantable(null));
        assertFalse(pack.isGrantable(""));
    }

    @Test
    @DisplayName("an unreadable pack leaves the mechanism inert rather than falling back to a guess")
    void unreadablePackFailsInert() {
        ConfidentialityPolicyPack broken = new ConfidentialityPolicyPack(null); // NPEs inside load()
        broken.load();

        assertFalse(broken.isEffective());
        assertEquals("ABSENT", broken.approvalStatus());
        assertTrue(broken.grantableCategories().isEmpty());
    }
}
