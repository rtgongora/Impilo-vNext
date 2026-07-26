package zw.gov.mohcc.impilo.tshepo.authz.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataSensitivityClass;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataVisibilityTier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resource classification, with the confidential lane as the focus.
 *
 * <p>Before this, nothing in the repository ever produced {@code SPECIALLY_PROTECTED} and the class
 * shared {@code FULL_IDENTIFIED_CLINICAL} with ordinary clinical data.</p>
 */
class ResourceSensitivityClassifierTest {

    @Test
    @DisplayName("the confidential lane markers classify as SPECIALLY_PROTECTED")
    void confidentialLane_classifiesAsSpeciallyProtected() {
        assertEquals(DataSensitivityClass.SPECIALLY_PROTECTED,
                ResourceSensitivityClassifier.classifyResource("confidential-encounters"));
        assertEquals(DataSensitivityClass.SPECIALLY_PROTECTED,
                ResourceSensitivityClassifier.classifyResource("safeguarding-concerns"));
        assertEquals(DataSensitivityClass.SPECIALLY_PROTECTED,
                ResourceSensitivityClassifier.classifyResource("protected-disclosure"));
        assertTrue(ResourceSensitivityClassifier.isSpeciallyProtected("CONFIDENTIAL-OBSERVATIONS"),
                "the lane marker must be case-insensitive");
    }

    @Test
    @DisplayName("the lane marker wins over the ordinary clinical branch it would otherwise match")
    void confidentialLane_isCheckedBeforeOrdinaryClinical() {
        // "confidential-encounters" contains "encounter"; if the ordinary clinical branch ran first
        // it would claim the resource and quietly downgrade it to FULL_CLINICAL.
        assertNotEquals(DataSensitivityClass.FULL_CLINICAL,
                ResourceSensitivityClassifier.classifyResource("confidential-encounters"));
        assertNotEquals(DataSensitivityClass.FULL_CLINICAL,
                ResourceSensitivityClassifier.classifyResource("confidential-observations"));
        assertNotEquals(DataSensitivityClass.IDENTIFIED_OPERATIONAL,
                ResourceSensitivityClassifier.classifyResource("confidential-patient-notes"));
    }

    @Test
    @DisplayName("SPECIALLY_PROTECTED gets its own ceiling, not the full-clinical one")
    void speciallyProtected_hasADistinctCeiling() {
        DataVisibilityTier protectedCeiling =
                ResourceSensitivityClassifier.maxTierForResource("confidential-encounters");

        assertEquals(DataVisibilityTier.SPECIALLY_PROTECTED_CLINICAL, protectedCeiling);
        assertNotEquals(ResourceSensitivityClassifier.maxTierForResource("encounters"), protectedCeiling,
                "sharing a ceiling with ordinary clinical data is precisely what made the class inert");
    }

    @Test
    @DisplayName("ordinary clinical and operational resources are unchanged")
    void ordinaryResources_areUnchanged() {
        assertEquals(DataSensitivityClass.FULL_CLINICAL,
                ResourceSensitivityClassifier.classifyResource("encounters"));
        assertEquals(DataSensitivityClass.FULL_CLINICAL,
                ResourceSensitivityClassifier.classifyResource("patients"));
        assertEquals(DataSensitivityClass.IDENTIFIED_OPERATIONAL,
                ResourceSensitivityClassifier.classifyResource("facility"));
        assertEquals(DataSensitivityClass.AGGREGATE_SENSITIVE,
                ResourceSensitivityClassifier.classifyResource("dashboard"));
        assertEquals(DataVisibilityTier.FULL_IDENTIFIED_CLINICAL,
                ResourceSensitivityClassifier.maxTierForResource("patients"));
    }

    @Test
    @DisplayName("nothing is protected by accident — no false positives on ordinary route names")
    void noFalsePositives() {
        for (String ordinary : new String[]{
                "patients", "encounters", "observations", "appointments", "prescriptions",
                "providers", "facility", "site", "assets", "equipment", "reports", "claims",
                "documents", "consents", "referrals", "wallet", "card"}) {
            assertFalse(ResourceSensitivityClassifier.isSpeciallyProtected(ordinary),
                    ordinary + " must not be swept into the confidential lane — a control that "
                            + "fires on ordinary care gets routed around");
        }
    }

    @Test
    @DisplayName("a blank or null resource type is not protected (and not sensitive)")
    void blankResourceType_isNotProtected() {
        assertFalse(ResourceSensitivityClassifier.isSpeciallyProtected(null));
        assertFalse(ResourceSensitivityClassifier.isSpeciallyProtected(""));
        assertEquals(DataSensitivityClass.NON_SENSITIVE_OPERATIONAL,
                ResourceSensitivityClassifier.classifyResource(null));
    }
}
