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
 * Pins the shipped state of the governed confidentiality pack.
 *
 * <p>The mechanism is complete; the content that decides behaviour is not, because it is a legal and
 * MoHCC policy question. The most important assertion in this file is the first one: <strong>what
 * ships is inert</strong>. If someone ratifies the pack without going through the governance
 * process, that test fails and tells them so.</p>
 */
class ConfidentialityPolicyPackTest {

    private ConfidentialityPolicyPack pack;

    @BeforeEach
    void setUp() {
        pack = new ConfidentialityPolicyPack(new ObjectMapper());
        pack.load();
    }

    @Test
    @DisplayName("the pack as shipped is INERT — it grants nothing and cannot be enforced")
    void shippedPackIsInert() {
        assertFalse(pack.isEffective(),
                "the pack must ship inert. Ratifying it requires MoHCC sign-off on the "
                        + "confidentiality ages and the confidential code list — if this test fails, "
                        + "confirm that governance actually happened rather than editing the test.");
        assertEquals("ENGINEERING_SEED", pack.approvalStatus());
        assertTrue(pack.grantableCategories().isEmpty(),
                "an inert pack grants no category, so no record can carry a protection label that "
                        + "does not protect it");
    }

    @Test
    @DisplayName("the pack loads and is readable even while inert")
    void inertPackStillLoads() {
        assertEquals("impilo.confidentiality.adolescent", pack.packId());
        assertEquals("engineering-seed-1.0.0", pack.version());
        assertTrue(pack.stateLabel().startsWith("INERT"),
                "the state must be legible in audit payloads and governance surfaces");
    }

    @Test
    @DisplayName("a named category is not grantable while the pack is inert")
    void namedCategoriesAreNotGrantableWhileInert() {
        for (String category : List.of("HIV", "SEXUAL_REPRODUCTIVE_HEALTH", "MENTAL_HEALTH",
                "SAFEGUARDING", "SUBSTANCE_USE", "GENDER_BASED_VIOLENCE")) {
            assertFalse(pack.isGrantable(category), category + " must not be grantable while inert");
        }
        assertTrue(pack.retainGrantable(List.of("HIV", "SAFEGUARDING")).isEmpty());
    }

    @Test
    @DisplayName("the whole-set grant survives an inert pack — it belongs to the mechanism, not the content")
    void wholeSetGrantSurvivesInertness() {
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
