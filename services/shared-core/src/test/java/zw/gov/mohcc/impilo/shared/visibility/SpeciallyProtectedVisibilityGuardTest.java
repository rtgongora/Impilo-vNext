package zw.gov.mohcc.impilo.shared.visibility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeciallyProtectedVisibilityGuardTest {

    private record Note(String id, String sensitivityClass, String category) {}

    private static final Note ORDINARY = new Note("n1", "FULL_CLINICAL", null);
    private static final Note SAFEGUARDING = new Note("n2", "SPECIALLY_PROTECTED", "SAFEGUARDING");
    private static final Note MENTAL_HEALTH = new Note("n3", "SPECIALLY_PROTECTED", "MENTAL_HEALTH");
    private static final Note UNSTAMPED = new Note("n4", null, null);

    @AfterEach
    void clearContext() {
        VisibilityContextHolder.clear();
        TrustContextHolder.clear();
    }

    private static VisibilityProfile granting(String... categories) {
        return VisibilityProfile.builder()
                .visibilityTier("FULL_IDENTIFIED_CLINICAL")
                .confidentialCategories(categories.length == 0 ? null : List.of(categories))
                .buildBare();
    }

    private static TrustContext ctxWithPurpose(String purpose) {
        return new TrustContext(UUID.randomUUID(), "actor-1", "PROVIDER", purpose,
                "fp", UUID.randomUUID(), null, null, null, AccessMode.EXTERNAL);
    }

    // ── Category scoping: the reason this is an obligation and not a tier ─────────

    @Test
    @DisplayName("a safeguarding grant does NOT confer mental-health access")
    void grantsAreCategoryScoped() {
        VisibilityProfile safeguardingOnly = granting("SAFEGUARDING");

        assertFalse(SpeciallyProtectedVisibilityGuard.withholds(
                        "SPECIALLY_PROTECTED", "SAFEGUARDING", safeguardingOnly, null),
                "the safeguarding lead must be able to read the disclosure they are acting on");
        assertTrue(SpeciallyProtectedVisibilityGuard.withholds(
                        "SPECIALLY_PROTECTED", "MENTAL_HEALTH", safeguardingOnly, null),
                "holding safeguarding must not sweep in the adolescent's mental-health notes — "
                        + "an ordered tier could not express this, which is why it is an obligation");
    }

    @Test
    @DisplayName("full clinical access alone confers no confidential category")
    void fullClinicalIsNotConfidentialAccess() {
        assertTrue(SpeciallyProtectedVisibilityGuard.withholds(
                        "SPECIALLY_PROTECTED", "HIV", granting(), null),
                "conflating full clinical access with confidential access is the original defect");
    }

    @Test
    @DisplayName("a whole-set grant covers every category, including an unlabelled protected record")
    void wholeSetGrant() {
        VisibilityProfile all = granting("*");

        assertFalse(SpeciallyProtectedVisibilityGuard.withholds("SPECIALLY_PROTECTED", "HIV", all, null));
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds("SPECIALLY_PROTECTED", null, all, null));
    }

    @Test
    @DisplayName("an unlabelled protected record is withheld from a category-scoped grant")
    void unlabelledProtectedRecordIsWithheld() {
        assertTrue(SpeciallyProtectedVisibilityGuard.withholds(
                        "SPECIALLY_PROTECTED", null, granting("SAFEGUARDING"), null),
                "a protected record with no category cannot be matched to a grant, so it stays closed");
    }

    // ── Fail-closed ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fails closed: no profile, no context, no disclosure")
    void failsClosed() {
        assertTrue(SpeciallyProtectedVisibilityGuard.withholds("SPECIALLY_PROTECTED", "HIV", null, null),
                "silence from the PDP must not mean disclosure for the highest confidentiality class");
        assertFalse(SpeciallyProtectedVisibilityGuard.allowsCategory("HIV", null));
        assertFalse(SpeciallyProtectedVisibilityGuard.allowsCategory("HIV"),
                "with no request context bound, nothing is disclosable");
    }

    @Test
    @DisplayName("ordinary and unstamped records are never withheld")
    void ordinaryRecordsAreNeverWithheld() {
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds("FULL_CLINICAL", null, granting(), null));
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds(null, null, granting(), null),
                "an unstamped record is ordinary data and must stay visible to those entitled to it");
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds("not-a-class", null, granting(), null),
                "an unreadable stamp must not silently withhold ordinary care");
    }

    // ── Emergency waiver (mirrors ClinicalAccessGuard) ────────────────────────────

    @Test
    @DisplayName("EMERGENCY purpose waives the category requirement")
    void emergencyPurposeWaives() {
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds(
                        "SPECIALLY_PROTECTED", "HIV", granting(), ctxWithPurpose("EMERGENCY")),
                "a teenager arriving unconscious must not have the HIV status that explains the "
                        + "presentation hidden from the clinician treating them");
    }

    @Test
    @DisplayName("BREAK_GLASS purpose waives the category requirement")
    void breakGlassWaives() {
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds(
                "SPECIALLY_PROTECTED", "MENTAL_HEALTH", granting(), ctxWithPurpose("BREAK_GLASS")));
    }

    @Test
    @DisplayName("the waiver survives a completely missing profile")
    void emergencyWaivesEvenWithNoProfile() {
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds(
                        "SPECIALLY_PROTECTED", "HIV", null, ctxWithPurpose("EMERGENCY")),
                "a service not yet wired to obligation headers must not become a place where "
                        + "emergency care fails");
    }

    @Test
    @DisplayName("an ordinary purpose does not waive")
    void ordinaryPurposeDoesNotWaive() {
        assertTrue(SpeciallyProtectedVisibilityGuard.withholds(
                "SPECIALLY_PROTECTED", "HIV", granting(), ctxWithPurpose("TREATMENT")));
        assertTrue(SpeciallyProtectedVisibilityGuard.withholds(
                "SPECIALLY_PROTECTED", "HIV", granting(), ctxWithPurpose("OPERATIONS")));
    }

    // ── Mixed collections ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a mixed collection drops only the categories not granted")
    void filterProtected_isPerCategory() {
        List<Note> all = List.of(ORDINARY, SAFEGUARDING, MENTAL_HEALTH, UNSTAMPED);

        List<Note> visible = SpeciallyProtectedVisibilityGuard.filterProtected(
                all, Note::sensitivityClass, Note::category, granting("SAFEGUARDING"), null);

        assertEquals(List.of(ORDINARY, SAFEGUARDING, UNSTAMPED), visible,
                "a request for a person's encounters is allowed even when some are confidential");
    }

    @Test
    @DisplayName("with no grant, every protected row is dropped and the rest survive")
    void filterProtected_noGrant() {
        List<Note> visible = SpeciallyProtectedVisibilityGuard.filterProtected(
                List.of(ORDINARY, SAFEGUARDING, MENTAL_HEALTH), Note::sensitivityClass, Note::category,
                granting(), null);

        assertEquals(List.of(ORDINARY), visible);
    }

    @Test
    @DisplayName("under emergency the whole collection survives")
    void filterProtected_emergency() {
        List<Note> all = List.of(ORDINARY, SAFEGUARDING, MENTAL_HEALTH);

        List<Note> visible = SpeciallyProtectedVisibilityGuard.filterProtected(
                all, Note::sensitivityClass, Note::category, granting(), ctxWithPurpose("EMERGENCY"));

        assertEquals(all, visible);
    }

    @Test
    @DisplayName("empty and null collections return an empty list, never null")
    void filterProtected_emptyInputs() {
        assertEquals(List.of(), SpeciallyProtectedVisibilityGuard.filterProtected(
                null, Note::sensitivityClass, Note::category, granting(), null));
        assertEquals(List.of(), SpeciallyProtectedVisibilityGuard.filterProtected(
                List.of(), Note::sensitivityClass, Note::category, granting(), null));
    }

    // ── Thread-bound context ──────────────────────────────────────────────────────

    @Test
    @DisplayName("the bound profile and trust context drive the no-argument overloads")
    void boundContextIsHonoured() {
        VisibilityContextHolder.set(granting("HIV"));

        assertTrue(SpeciallyProtectedVisibilityGuard.allowsCategory("HIV"));
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds("SPECIALLY_PROTECTED", "HIV"));
        assertTrue(SpeciallyProtectedVisibilityGuard.withholds("SPECIALLY_PROTECTED", "SAFEGUARDING"));

        TrustContextHolder.set(ctxWithPurpose("EMERGENCY"));
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds("SPECIALLY_PROTECTED", "SAFEGUARDING"),
                "the emergency waiver applies through the bound trust context too");
    }
}
