package zw.gov.mohcc.impilo.shared.visibility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeciallyProtectedVisibilityGuardTest {

    private record Note(String id, String sensitivityClass) {}

    private static final Note ORDINARY = new Note("n1", "FULL_CLINICAL");
    private static final Note PROTECTED = new Note("n2", "SPECIALLY_PROTECTED");
    private static final Note UNSTAMPED = new Note("n3", null);

    @AfterEach
    void clearContext() {
        VisibilityContextHolder.clear();
    }

    private static VisibilityProfile profileWithTier(String tier) {
        return VisibilityProfile.builder().visibilityTier(tier).buildBare();
    }

    @Test
    @DisplayName("only the protected tier admits protected content")
    void allowsProtectedContent_onlyAtTheProtectedTier() {
        assertTrue(SpeciallyProtectedVisibilityGuard.allowsProtectedContent(
                profileWithTier("SPECIALLY_PROTECTED_CLINICAL")));
        assertFalse(SpeciallyProtectedVisibilityGuard.allowsProtectedContent(
                profileWithTier("FULL_IDENTIFIED_CLINICAL")),
                "full clinical access is not confidential access — that conflation is the original defect");
    }

    @Test
    @DisplayName("fails closed: a missing profile or unparseable tier admits nothing")
    void failsClosed() {
        assertFalse(SpeciallyProtectedVisibilityGuard.allowsProtectedContent((VisibilityProfile) null),
                "silence from the PDP must not mean disclosure for the highest confidentiality class");
        assertFalse(SpeciallyProtectedVisibilityGuard.allowsProtectedContent(profileWithTier(null)));
        assertFalse(SpeciallyProtectedVisibilityGuard.allowsProtectedContent(profileWithTier("nonsense")));
        assertFalse(SpeciallyProtectedVisibilityGuard.allowsProtectedContent(),
                "with no request context bound, nothing is disclosable");
    }

    @Test
    @DisplayName("withholds a protected record from an unentitled requester, and nothing else")
    void withholds_onlyProtectedAndOnlyUnentitled() {
        VisibilityProfile unentitled = profileWithTier("FULL_IDENTIFIED_CLINICAL");
        VisibilityProfile entitled = profileWithTier("SPECIALLY_PROTECTED_CLINICAL");

        assertTrue(SpeciallyProtectedVisibilityGuard.withholds("SPECIALLY_PROTECTED", unentitled));
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds("SPECIALLY_PROTECTED", entitled));
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds("FULL_CLINICAL", unentitled),
                "ordinary clinical records must not be withheld — the control narrows protected data only");
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds(null, unentitled),
                "an unstamped record is ordinary data and must stay visible to those entitled to it");
    }

    @Test
    @DisplayName("mixed collections drop protected rows and keep the rest")
    void filterProtected_dropsOnlyProtectedRows() {
        List<Note> all = List.of(ORDINARY, PROTECTED, UNSTAMPED);

        List<Note> visible = SpeciallyProtectedVisibilityGuard.filterProtected(
                all, Note::sensitivityClass, profileWithTier("FULL_IDENTIFIED_CLINICAL"));

        assertEquals(List.of(ORDINARY, UNSTAMPED), visible,
                "a request for a person's encounters is allowed even when three of them are confidential");
    }

    @Test
    @DisplayName("an entitled requester sees the collection unchanged")
    void filterProtected_entitledSeesEverything() {
        List<Note> all = List.of(ORDINARY, PROTECTED, UNSTAMPED);

        List<Note> visible = SpeciallyProtectedVisibilityGuard.filterProtected(
                all, Note::sensitivityClass, profileWithTier("SPECIALLY_PROTECTED_CLINICAL"));

        assertSame(all, visible);
    }

    @Test
    @DisplayName("with no bound context a collection is filtered, not passed through")
    void filterProtected_noContextFailsClosed() {
        List<Note> visible = SpeciallyProtectedVisibilityGuard.filterProtected(
                List.of(ORDINARY, PROTECTED), Note::sensitivityClass);

        assertEquals(List.of(ORDINARY), visible);
    }

    @Test
    @DisplayName("the thread-bound profile is honoured by the no-argument overloads")
    void currentContext_isHonoured() {
        VisibilityContextHolder.set(profileWithTier("SPECIALLY_PROTECTED_CLINICAL"));

        assertTrue(SpeciallyProtectedVisibilityGuard.allowsProtectedContent());
        assertFalse(SpeciallyProtectedVisibilityGuard.withholds("SPECIALLY_PROTECTED"));
    }

    @Test
    @DisplayName("empty and null collections are handled without a null return")
    void filterProtected_emptyInputs() {
        assertEquals(List.of(), SpeciallyProtectedVisibilityGuard.filterProtected(
                null, Note::sensitivityClass, profileWithTier("FULL_IDENTIFIED_CLINICAL")));
        assertEquals(List.of(), SpeciallyProtectedVisibilityGuard.filterProtected(
                List.of(), Note::sensitivityClass, profileWithTier("FULL_IDENTIFIED_CLINICAL")));
    }
}
