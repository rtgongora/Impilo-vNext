package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataVisibilityTier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The confidential-category obligation — the third visibility axis, alongside PII access and
 * clinical access and deliberately NOT a rung on the {@link DataVisibilityTier} ladder.
 */
class VisibilityProfileConfidentialityTest {

    private static VisibilityProfile granting(String... categories) {
        return VisibilityProfile.builder()
                .visibilityTier(DataVisibilityTier.FULL_IDENTIFIED_CLINICAL.name())
                .confidentialCategories(categories.length == 0 ? null : List.of(categories))
                .buildBare();
    }

    @Test
    @DisplayName("grants are category-scoped: safeguarding does not confer mental health")
    void grantsAreCategoryScoped() {
        VisibilityProfile profile = granting("SAFEGUARDING");

        assertTrue(profile.allowsConfidentialCategory("SAFEGUARDING"));
        assertFalse(profile.allowsConfidentialCategory("MENTAL_HEALTH"),
                "a total-order tier could not express this, which is why confidentiality is an "
                        + "obligation and not a tier");
    }

    @Test
    @DisplayName("fails closed: no categories granted means no protected content")
    void failsClosed() {
        assertFalse(granting().allowsConfidentialCategory("HIV"));
        assertFalse(granting().allowsAnyConfidentialCategory());
        assertFalse(granting("HIV").allowsConfidentialCategory(null));
        assertFalse(granting("HIV").allowsConfidentialCategory(""));
    }

    @Test
    @DisplayName("full clinical access alone confers no confidential category")
    void fullClinicalIsOrthogonal() {
        VisibilityProfile fullClinical = VisibilityProfile.builder()
                .visibilityTier(DataVisibilityTier.FULL_IDENTIFIED_CLINICAL.name())
                .piiAccess("FULL")
                .clinicalAccess("FULL")
                .buildBare();

        assertFalse(fullClinical.allowsAnyConfidentialCategory(),
                "conflating full clinical access with confidential access is the original defect");
    }

    @Test
    @DisplayName("a whole-set grant covers every category")
    void wholeSetGrant() {
        VisibilityProfile all = granting("*");

        assertTrue(all.allowsConfidentialCategory("HIV"));
        assertTrue(all.allowsConfidentialCategory("SAFEGUARDING"));
        assertTrue(all.allowsAnyConfidentialCategory());
    }

    @Test
    @DisplayName("matching is case- and whitespace-insensitive")
    void matchingIsNormalised() {
        assertTrue(granting(" hiv ").allowsConfidentialCategory("HIV"));
        assertTrue(granting("HIV").allowsConfidentialCategory(" hiv "));
    }

    @Test
    @DisplayName("the builder unions grants and revokes them wholesale")
    void builderGrantAndRevoke() {
        VisibilityProfile.Builder b = VisibilityProfile.builder();
        b.grantConfidentialCategories(List.of("SAFEGUARDING"));
        b.grantConfidentialCategories(List.of("SAFEGUARDING", "GENDER_BASED_VIOLENCE"));

        assertEquals(List.of("SAFEGUARDING", "GENDER_BASED_VIOLENCE"),
                b.build().confidentialCategories(), "grants union without duplicating");

        b.revokeConfidentialCategories();
        assertNull(b.build().confidentialCategories(),
                "revocation must be wholesale — it is the clamp that has to win over any overlay");
    }

    @Test
    @DisplayName("the pre-confidentiality constructor still works and grants nothing")
    void legacyArityGrantsNothing() {
        VisibilityProfile legacy = new VisibilityProfile(
                "FULL_IDENTIFIED_CLINICAL", "FULL", "FULL", false, "FULL_CLINICAL",
                null, null, null, null, "FULL_AUDITED", true);

        assertNull(legacy.confidentialCategories());
        assertFalse(legacy.allowsAnyConfidentialCategory(),
                "existing construction sites must keep compiling AND keep granting nothing");
    }

    @Test
    @DisplayName("seeding a builder from a profile carries the grants across")
    void builderSeedCarriesGrants() {
        VisibilityProfile seeded = VisibilityProfile.builder(granting("HIV")).build();

        assertEquals(List.of("HIV"), seeded.confidentialCategories());
    }
}
