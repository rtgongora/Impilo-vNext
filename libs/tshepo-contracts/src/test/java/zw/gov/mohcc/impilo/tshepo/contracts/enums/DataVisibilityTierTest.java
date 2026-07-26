package zw.gov.mohcc.impilo.tshepo.contracts.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The visibility lattice, with particular attention to {@code SPECIALLY_PROTECTED_CLINICAL}.
 *
 * <p>That tier exists so the {@code SPECIALLY_PROTECTED} sensitivity class has somewhere distinct
 * to land. Before it, the class mapped to {@code FULL_IDENTIFIED_CLINICAL} — the same tier ordinary
 * clinical data gets — so marking a record protected changed nothing about who could read it.</p>
 */
class DataVisibilityTierTest {

    @Test
    @DisplayName("SPECIALLY_PROTECTED_CLINICAL sits strictly above FULL_IDENTIFIED_CLINICAL")
    void speciallyProtected_isStrictlyAboveFullClinical() {
        assertTrue(DataVisibilityTier.SPECIALLY_PROTECTED_CLINICAL.disclosureLevel()
                        > DataVisibilityTier.FULL_IDENTIFIED_CLINICAL.disclosureLevel(),
                "a distinct tier is the whole point — sharing FULL_IDENTIFIED_CLINICAL is what made "
                        + "the sensitivity class decorative");
    }

    @Test
    @DisplayName("only the protected tier admits specially-protected content")
    void allowsSpeciallyProtected_onlyAtTheTopTier() {
        for (DataVisibilityTier tier : DataVisibilityTier.values()) {
            if (tier == DataVisibilityTier.SPECIALLY_PROTECTED_CLINICAL) {
                assertTrue(tier.allowsSpeciallyProtected(), tier + " must admit protected content");
            } else {
                assertFalse(tier.allowsSpeciallyProtected(),
                        tier + " must NOT admit protected content — including full clinical access");
            }
        }
    }

    @Test
    @DisplayName("the protected tier is a superset: full clinical detail and direct identifiers")
    void protectedTier_isASupersetOfFullClinical() {
        DataVisibilityTier protectedTier = DataVisibilityTier.SPECIALLY_PROTECTED_CLINICAL;

        assertTrue(protectedTier.allowsFullClinical(),
                "a higher tier that reports less clinical access than the tier below it would "
                        + "silently downgrade the very actors it is meant to privilege");
        assertTrue(protectedTier.allowsDirectIdentifiers());
        assertTrue(protectedTier.allowsRowLevel());
    }

    @Test
    @DisplayName("min() caps a protected grant down to an ordinary ceiling; max() lifts to protected")
    void latticeComposition_behavesForTheNewTier() {
        assertEquals(DataVisibilityTier.FULL_IDENTIFIED_CLINICAL,
                DataVisibilityTier.min(DataVisibilityTier.SPECIALLY_PROTECTED_CLINICAL,
                        DataVisibilityTier.FULL_IDENTIFIED_CLINICAL));
        assertEquals(DataVisibilityTier.SPECIALLY_PROTECTED_CLINICAL,
                DataVisibilityTier.max(DataVisibilityTier.FULL_IDENTIFIED_CLINICAL,
                        DataVisibilityTier.SPECIALLY_PROTECTED_CLINICAL));
    }

    @Test
    @DisplayName("fromString round-trips the protected tier and stays fail-safe on junk")
    void fromString_roundTripsAndFailsSafe() {
        assertEquals(DataVisibilityTier.SPECIALLY_PROTECTED_CLINICAL,
                DataVisibilityTier.fromString("specially_protected_clinical"));
        assertEquals(DataVisibilityTier.IDENTIFIED_OPERATIONAL_ONLY,
                DataVisibilityTier.fromString("not-a-tier"),
                "an unparseable tier must never fall back to a protected or full-clinical grant");
    }

    @Test
    @DisplayName("existing constants keep their ordinals (the new tier was appended)")
    void ordinalsAreStable() {
        assertEquals(0, DataVisibilityTier.AGGREGATE_ONLY.ordinal());
        assertEquals(5, DataVisibilityTier.FULL_IDENTIFIED_CLINICAL.ordinal());
        assertEquals(6, DataVisibilityTier.SPECIALLY_PROTECTED_CLINICAL.ordinal());
    }
}
