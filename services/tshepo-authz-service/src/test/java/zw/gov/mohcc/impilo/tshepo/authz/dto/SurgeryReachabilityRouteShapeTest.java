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
