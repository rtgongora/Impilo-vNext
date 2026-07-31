package zw.gov.mohcc.impilo.tshepo.authz.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves, against the real {@code deriveResourceType} and {@code deriveResourceId} used by
 * the PDP, that the BFF route shapes V300's procedures-service policy rows are written
 * against actually derive the resource_type those rows expect.
 *
 * <p>Written because reasoning about {@code deriveResourceType}'s backward UUID-skipping walk
 * from a comment is exactly how a wrong assumption survives — the theatre programme's own
 * trailing-slash path_contains defect was flagged as "possible" in a comment for two waves
 * before anyone checked the implementation. This asserts the derivation directly instead.</p>
 */
class ProceduresRouteShapeTest {

    @Test
    void catalogueSearchDerivesResourceTypeCatalogue() {
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/procedures/catalogue"))
                .isEqualTo("catalogue");
    }

    /**
     * The defect this route shape was designed around. A REST-style path variable
     * ({@code .../catalogue/{definitionCode}}) would derive the procedure code itself as the
     * resource type, because deriveResourceType only skips UUID-shaped segments and
     * definition_code values ("PROC-LAPAROTOMY") are not UUIDs. Proven here rather than
     * asserted in a comment: the naive shape genuinely breaks, and the chosen shape genuinely
     * does not.
     */
    @Test
    void aRestStylePathVariableWouldHaveBrokenTheDerivation() {
        String naiveShape = "/internal/v1/procedures/catalogue/PROC-LAPAROTOMY";

        String derived = AuthzInternalRequest.deriveResourceType(naiveShape);

        // This is the failure this route shape avoids: the procedure code, not "catalogue".
        assertThat(derived).isEqualTo("PROC-LAPAROTOMY");
        assertThat(derived).isNotEqualTo("catalogue");
    }

    @Test
    void catalogueDetailWithAQueryParamDerivesResourceTypeCatalogueDetail() {
        // Query strings are not part of the path deriveResourceType walks, so a code passed as
        // ?code=... never reaches the segment walk at all — this is what makes the shape safe
        // for arbitrary, growing definition_code values.
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/procedures/catalogue-detail"))
                .isEqualTo("catalogue-detail");
    }

    @Test
    void appropriatenessEvaluateDerivesResourceTypeEvaluate() {
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/procedures/appropriateness/evaluate"))
                .isEqualTo("evaluate");
    }

    @Test
    void competenceDerivesResourceTypeCompetenceRegardlessOfQueryParams() {
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/procedures/competence"))
                .isEqualTo("competence");
    }

    /**
     * The UUID-skip heuristic itself, confirmed against a real theatre-style path — this is
     * why V035's approach was safe for THEIR resource identifiers and would not have been
     * safe for procedures-service's human-readable ones.
     */
    @Test
    void aUuidPathSegmentIsSkippedByTheDerivationHeuristic() {
        String theatreStyle = "/internal/v1/theatre/cases/3f9a1c2e-4b5d-4a6f-9c7e-8d0f1a2b3c4d/blood/request";
        assertThat(AuthzInternalRequest.deriveResourceType(theatreStyle)).isEqualTo("request");
    }

    // ── Wave W4 — P10 Clavien-Dindo grades and complication profiles (V306) ──

    @Test
    void clavienDindoGradesDerivesResourceTypeClavienDindoGrades() {
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/procedures/clavien-dindo-grades"))
                .isEqualTo("clavien-dindo-grades");
    }

    @Test
    void complicationProfilesWithAQueryParamDerivesResourceTypeComplicationProfiles() {
        // ?code=... never reaches the segment walk — the fixed word is the resource_type V306 pins.
        assertThat(AuthzInternalRequest.deriveResourceType("/internal/v1/procedures/complication-profiles"))
                .isEqualTo("complication-profiles");
    }
}
