package zw.gov.mohcc.impilo.madi.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.sharedkernel.security.EscalationGrantValidator.Outcome;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The HTTP-outcome mapping, which is where the emergency-access ruling is actually implemented.
 *
 * <h2>Why this test exists</h2>
 * <p>Everything above this class mocks the validator, so the guard's three branches were fully tested
 * while the mapping that decides <em>which</em> branch a real request lands in had no test at all.
 * That gap hid a defect that would have refused every break-glass action in production: MADI sent no
 * {@code Authorization} header, tshepo-authz answered 401, and 401 was being mapped to
 * {@code NO_GRANT}.</p>
 *
 * <p>The rule now is simple enough to state in one line, and these tests hold it: <b>only an explicit
 * 200 with a recognised outcome is an answer.</b> Everything else is {@code UNREACHABLE}.</p>
 */
class TshepoEscalationGrantValidatorTest {

    private static final String URL =
            "http://tshepo-authz/v1/visibility-escalations/grants/validate";

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    private final TshepoEscalationGrantValidator validator =
            new TshepoEscalationGrantValidator(restTemplate, "http://tshepo-authz");

    private Outcome ask() {
        return validator.validate("tenant-1", "clinician-7", "grant-1");
    }

    private void answers(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    // ── the two real answers ──────────────────────────────────────────────────

    @Test
    void anExplicitValidIsTheOnlyThingThatGrantsAccess() {
        answers("{\"outcome\":\"VALID\",\"visibilityCeiling\":\"FULL\"}");
        assertEquals(Outcome.VALID, ask());
    }

    @Test
    void anExplicitNoGrantIsARefusal() {
        answers("{\"outcome\":\"NO_GRANT\"}");
        assertEquals(Outcome.NO_GRANT, ask());
    }

    // ── everything else is "we did not get an answer" ─────────────────────────

    /**
     * The defect this test was written for. MADI sent no Authorization header, so tshepo-authz
     * answered 401 — and mapping that to NO_GRANT refused every emergency action in the estate,
     * silently, with a clinical consequence.
     */
    @Test
    void anUnauthenticatedCallIsUnreachableRatherThanARefusal() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertEquals(Outcome.UNREACHABLE, ask(),
                "401 means the question was not answered, not that the clinician has no grant");
    }

    @Test
    void aForbiddenCallIsUnreachableRatherThanARefusal() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertEquals(Outcome.UNREACHABLE, ask());
    }

    /** A stale route or a wrong base URL is a misconfiguration, not a statement about the grant. */
    @Test
    void aMisroutedCallIsUnreachable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertEquals(Outcome.UNREACHABLE, ask());
    }

    @Test
    void aServerErrorIsUnreachable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertEquals(Outcome.UNREACHABLE, ask());
    }

    @Test
    void aTimeoutIsUnreachable() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
        assertEquals(Outcome.UNREACHABLE, ask());
    }

    /** Something answered on that port, but it was not this endpoint. */
    @Test
    void anUnusableBodyIsUnreachable() {
        answers("{\"something\":\"else\"}");
        assertEquals(Outcome.UNREACHABLE, ask());
    }

    /**
     * A future outcome this build does not understand must not be read as permission. Only the
     * literal VALID grants access.
     */
    @Test
    void anUnknownOutcomeIsUnreachableAndNeverValid() {
        answers("{\"outcome\":\"PROBABLY\"}");
        assertEquals(Outcome.UNREACHABLE, ask());
    }

    // ── nothing to ask about ──────────────────────────────────────────────────

    /**
     * A caller presenting no grant has no grant — that is an answer we can give without asking, and
     * it must stay a refusal rather than becoming a free override.
     */
    @Test
    void presentingNoGrantIsARefusalWithoutCallingTheTrustPlane() {
        assertEquals(Outcome.NO_GRANT, validator.validate("tenant-1", "clinician-7", null));
        assertEquals(Outcome.NO_GRANT, validator.validate("tenant-1", "clinician-7", "  "));
        server.verify(); // no request was made
    }
}
