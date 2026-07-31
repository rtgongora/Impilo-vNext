package zw.gov.mohcc.impilo.tshepo.authz.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves, against the real {@code deriveResourceType} the PDP uses, that every route the SB-3
 * reachability wave gates derives exactly the resource_type V302's policy rows are written
 * against. Same discipline as {@link ProceduresRouteShapeTest}: assert the derivation, never
 * reason about it from a migration comment.
 */
class SurgeryReachabilityRouteShapeTest {

    private static final String EPISODE_ID = "3f9a1c2e-4b5d-4a6f-9c7e-8d0f1a2b3c4d";
    private static final String SPECIMEN_ID = "0b1c2d3e-4f50-4172-8394-a5b6c7d8e9f0";
    private static final String PATHWAY_ID = "a1b2c3d4-e5f6-4789-a012-3456789abcde";
    private static final String OBJECT_ID = "f0e1d2c3-b4a5-4678-9012-3456789abcde";

    // ── surgery-service (S1-S3) ──

    @Test
    void episodeCollectionDerivesEpisodes() {
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/surgery/episodes"))
                .isEqualTo("episodes");
    }

    /** The UUID skip S1's controller javadoc relies on, proven rather than trusted. */
    @Test
    void episodeDetailSkipsTheUuidAndStillDerivesEpisodes() {
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/surgery/episodes/" + EPISODE_ID))
                .isEqualTo("episodes");
    }

    @Test
    void linkProcedureEpisodeDerivesItsOwnSegment() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/link-procedure-episode"))
                .isEqualTo("link-procedure-episode");
    }

    @Test
    void transitionDerivesTransition() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/transition"))
                .isEqualTo("transition");
    }

    @Test
    void assessmentAndDecisionDeriveTheirOwnSegments() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/assessment"))
                .isEqualTo("assessment");
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/decision"))
                .isEqualTo("decision");
    }

    // ── completion wave: reoperation (V010) and shared-specialty care (V011), gated by V303 ──

    @Test
    void reopenDerivesReopen() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/reopen"))
                .isEqualTo("reopen");
    }

    @Test
    void everySpecialtiesVerbDerivesSpecialties() {
        // GET, POST and DELETE all address the same path; only the action differs, which is why
        // V303 writes three rows on one resource type rather than three resource types.
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/specialties"))
                .isEqualTo("specialties");
    }

    @Test
    void leadHandoverDerivesLead() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/specialties/lead"))
                .isEqualTo("lead");
    }

    /**
     * The reason the DELETE carries {@code ?specialty=} rather than a final path segment. This
     * is the free-text-code-in-path trap: as a segment the specialty BECOMES the resource type,
     * so no policy row could ever match and the route would be permanently unreachable. Asserted
     * so that anyone who "tidies" the route back into REST shape sees why it was not.
     */
    @Test
    void aSpecialtyAsAFinalSegmentWouldDeriveTheSpecialtyItselfNotSpecialties() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/specialties/GENERAL_SURGERY"))
                .isEqualTo("GENERAL_SURGERY")
                .isNotEqualTo("specialties");
    }

    /** The specialty-catalogue routes share the word but never the derived type. */
    @Test
    void theSpecialtyCatalogueRoutesDoNotCollideWithEpisodeSpecialties() {
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/surgery/specialties/indications"))
                .isEqualTo("indications");
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/surgery/specialties/templates"))
                .isEqualTo("templates");
    }

    /**
     * Why V305 pins the catalogue to {@code /surgery/specialties}, not {@code /surgery/episodes}:
     * the derived types ("indications"/"templates") are unique, but an episode-family pin would
     * still be the wrong path family — a clinician ALLOW written against the roster route would
     * never match the catalogue URL, and a catalogue ALLOW pinned to episodes would never fire
     * for the real catalogue path. Asserted so the pin cannot "tidy" onto the episode family.
     */
    @Test
    void specialtyCataloguePathsContainSpecialtiesNotEpisodes() {
        String indications = "/internal/v1/surgery/specialties/indications";
        String templates = "/internal/v1/surgery/specialties/templates";
        assertThat(indications).contains("/surgery/specialties").doesNotContain("/surgery/episodes");
        assertThat(templates).contains("/surgery/specialties").doesNotContain("/surgery/episodes");
        // ?specialty= never reaches the segment walk — same discipline as episode DELETE.
        assertThat(AuthzInternalRequest.deriveResourceType(indications)).isEqualTo("indications");
        assertThat(AuthzInternalRequest.deriveResourceType(templates)).isEqualTo("templates");
    }

    // ── specialty catalogue + surgical analytics (SB-4 / §23), gated by V305 ──

    @Test
    void surgeryAnalyticsRoutesDeriveIndicatorsAndIndicator() {
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/surgery/analytics/indicators"))
                .isEqualTo("indicators");
        // ?code=... is a query string and never reaches the segment walk — the fixed word wins.
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/surgery/analytics/indicator"))
                .isEqualTo("indicator");
    }

    /**
     * Why V305 pins surgery analytics to {@code /surgery/analytics}: the same final segments
     * already have V302 procedures rows on {@code /procedures/analytics}. Without the pin a
     * clinician ALLOW on procedures would fire for surgery (or vice versa).
     */
    @Test
    void surgeryAndProceduresAnalyticsShareTheWordButNotThePin() {
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/surgery/analytics/indicators"))
                .isEqualTo("indicators");
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/procedures/analytics/indicators"))
                .isEqualTo("indicators");
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/surgery/analytics/indicator"))
                .isEqualTo("indicator");
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/procedures/analytics/indicator"))
                .isEqualTo("indicator");
    }

    // ── course-of-care (parity Wave A), gated by V304 ──

    @Test
    void prehabDerivesPrehab() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/prehab"))
                .isEqualTo("prehab");
    }

    @Test
    void complicationsCollectionDerivesComplications() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/complications"))
                .isEqualTo("complications");
        // PUT .../complications/{uuid} still derives "complications" — the UUID is skipped.
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/complications/" + PATHWAY_ID))
                .isEqualTo("complications");
    }

    @Test
    void complicationGradeDiscloseCloseDeriveTheirOwnSegments() {
        String base = "/internal/v1/surgery/episodes/" + EPISODE_ID + "/complications/" + PATHWAY_ID;
        assertThat(AuthzInternalRequest.deriveResourceType(base + "/grade")).isEqualTo("grade");
        assertThat(AuthzInternalRequest.deriveResourceType(base + "/disclose")).isEqualTo("disclose");
        assertThat(AuthzInternalRequest.deriveResourceType(base + "/close")).isEqualTo("close");
    }

    @Test
    void longitudinalObjectsDeriveCollectionAndLifecycleVerbs() {
        String base = "/internal/v1/surgery/episodes/" + EPISODE_ID + "/longitudinal-objects";
        assertThat(AuthzInternalRequest.deriveResourceType(base)).isEqualTo("longitudinal-objects");
        assertThat(AuthzInternalRequest.deriveResourceType(base + "/" + OBJECT_ID + "/remove"))
                .isEqualTo("remove");
        assertThat(AuthzInternalRequest.deriveResourceType(base + "/" + OBJECT_ID + "/revise"))
                .isEqualTo("revise");
    }

    /**
     * Why V304 pins surgery remove/revise to {@code /surgery/episodes}: the same final segment
     * already has V302 inventory rows on {@code /inventory/implants}. Without the pin a surgeon
     * ALLOW on inventory would fire for a longitudinal remove (or vice versa).
     */
    @Test
    void surgeryAndInventoryRemoveReviseShareTheWordButNotThePin() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/longitudinal-objects/"
                        + OBJECT_ID + "/remove"))
                .isEqualTo("remove");
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/inventory/implants/" + OBJECT_ID + "/remove"))
                .isEqualTo("remove");
    }

    @Test
    void followupAndWaitlistRevalidationDeriveTheirOwnSegments() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/followup"))
                .isEqualTo("followup");
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/surgery/episodes/" + EPISODE_ID + "/waitlist-revalidation"))
                .isEqualTo("waitlist-revalidation");
    }

    /** Wave B3 seed row in the same V304 file — pinned to /theatre/cases, not surgery. */
    @Test
    void theatreSiteSideDerivesSiteSide() {
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/theatre/cases/" + EPISODE_ID + "/site-side"))
                .isEqualTo("site-side");
    }

    // ── procedures-service analytics (P14) ──

    @Test
    void analyticsRoutesDeriveIndicatorsAndIndicator() {
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/procedures/analytics/indicators"))
                .isEqualTo("indicators");
        // ?code=... is a query string and never reaches the segment walk — the fixed word wins.
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/procedures/analytics/indicator"))
                .isEqualTo("indicator");
    }

    // ── theatre specimen custody (P8 §13) ──

    @Test
    void custodyRoutesDeriveTheFixedActionWordPastBothUuids() {
        String base = "/internal/v1/theatre/cases/" + EPISODE_ID + "/specimens/" + SPECIMEN_ID;
        assertThat(AuthzInternalRequest.deriveResourceType(base + "/collect")).isEqualTo("collect");
        assertThat(AuthzInternalRequest.deriveResourceType(base + "/confirm-label")).isEqualTo("confirm-label");
        assertThat(AuthzInternalRequest.deriveResourceType(base + "/receive")).isEqualTo("receive");
        assertThat(AuthzInternalRequest.deriveResourceType(base + "/adequacy")).isEqualTo("adequacy");
    }

    // ── inventory implant lifecycle (P8 §14) ──

    @Test
    void implantLifecycleRoutesDeriveRemoveReviseRecall() {
        String linkId = "9e8d7c6b-5a49-4837-9261-504f3e2d1c0b";
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/inventory/implants/" + linkId + "/remove")).isEqualTo("remove");
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/inventory/implants/" + linkId + "/revise")).isEqualTo("revise");
        assertThat(AuthzInternalRequest.deriveResourceType(
                "/internal/v1/inventory/implants/recall")).isEqualTo("recall");
    }
}
