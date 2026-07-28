package zw.gov.mohcc.impilo.tshepo.authz.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves, by reflection against the real private {@code pathContainsSegment}, the defect this
 * migration's rules were written to avoid: a {@code path_contains} pin ending in a trailing
 * slash never matches a path with anything after that slash.
 *
 * <p>Reflection rather than the full {@code PolicyEngine.evaluate()} harness deliberately.
 * {@code pathContainsSegment} is a pure function of two strings with no collaborators; routing
 * through the full seven-step decision algorithm (risk scoring, purpose, break-glass, RBAC/ABAC,
 * consent, step-up, obligations — see {@code PolicyEngineTest}) to reach one boolean check would
 * need a dozen mocks that have nothing to do with what is being proven here.</p>
 *
 * <p>This is the check that turned "possibly a latent defect", as the theatre programme's own
 * V029/V030 comment hedged it, into a confirmed one — and the reason every {@code path_contains}
 * value in V300 omits the trailing slash.</p>
 */
class PathContainsSegmentTest {

    private static boolean pathContainsSegment(String path, String pin) throws Exception {
        Method m = PolicyEngine.class.getDeclaredMethod("pathContainsSegment", String.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, path, pin);
    }

    @Test
    void aTrailingSlashPinNeverMatchesAPathWithAnythingAfterIt() throws Exception {
        // The exact shape V029/V030 used, and the exact shape of path it was meant to gate.
        boolean matched = pathContainsSegment(
                "/internal/v1/theatre/cases/3f9a1c2e-4b5d-4a6f-9c7e-8d0f1a2b3c4d/blood/request",
                "/theatre/cases/");

        assertThat(matched)
                .as("a trailing-slash pin requires the NEXT character after its own trailing "
                        + "slash to also be '/' or end-of-string — which an id segment never is")
                .isFalse();
    }

    @Test
    void theSamePinWithoutTheTrailingSlashMatchesCorrectly() throws Exception {
        boolean matched = pathContainsSegment(
                "/internal/v1/theatre/cases/3f9a1c2e-4b5d-4a6f-9c7e-8d0f1a2b3c4d/blood/request",
                "/theatre/cases");

        assertThat(matched).isTrue();
    }

    @Test
    void aTrailingSlashPinDoesMatchWhenThePathLiterallyEndsThere() throws Exception {
        // Confirms the failure is specific to "something follows", not the trailing slash per
        // se — a pin like this only ever succeeds by coincidence, when nothing is nested under it.
        assertThat(pathContainsSegment("/internal/v1/theatre/cases/", "/theatre/cases/")).isTrue();
    }

    /** The exact pins used by V300 for procedures-service, proven to match the real BFF routes. */
    @Test
    void v300ProcedurePinsMatchTheRoutesTheyGate() throws Exception {
        assertThat(pathContainsSegment("/internal/v1/procedures/catalogue", "/procedures/catalogue")).isTrue();
        assertThat(pathContainsSegment("/internal/v1/procedures/catalogue-detail", "/procedures/catalogue-detail")).isTrue();
        assertThat(pathContainsSegment("/internal/v1/procedures/appropriateness/evaluate", "/procedures/appropriateness")).isTrue();
        assertThat(pathContainsSegment("/internal/v1/procedures/competence", "/procedures/competence")).isTrue();
    }

    /** And that the catalogue pin does not over-match an unrelated route sharing a prefix word. */
    @Test
    void theCataloguePinDoesNotMatchAnUnrelatedRouteSharingAPrefix() throws Exception {
        assertThat(pathContainsSegment("/internal/v1/procedures/catalogue-export-audit", "/procedures/catalogue"))
                .as("segment-boundary check: 'catalogue-export-audit' must not satisfy a pin on 'catalogue'")
                .isFalse();
    }
}
