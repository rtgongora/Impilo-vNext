package zw.gov.mohcc.impilo.experience.trust;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import zw.gov.mohcc.impilo.experience.auth.session.WebAuthSessionStore;
import zw.gov.mohcc.impilo.experience.client.TrustDecisionResult;
import zw.gov.mohcc.impilo.experience.controller.GlobalExceptionHandler;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeOutcome;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every governance service in this module refused identically: {@code 403} plus a sentence written
 * for a log. The status carried no information (an outage answered 403 too) and the body carried
 * nothing renderable — no reason code, no permitted next step, no way to resume.
 *
 * <p>These tests cover the whole path a refusal now travels: decision → responder → exception →
 * HTTP envelope.</p>
 */
class TrustChallengeResponderTest {

    private WebAuthSessionStore store;
    private TrustChallengeResponder responder;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        store = mock(WebAuthSessionStore.class);
        when(store.saveContinuation(anyString(), any(), any())).thenReturn("cont-xyz");
        responder = new TrustChallengeResponder(store);
        handler = new GlobalExceptionHandler();
    }

    private static TrustChallengeOutcome outcome(TrustChallengeDecision d, String reason) {
        return TrustChallengeOutcome.of(d, reason, "trust.k", "ACT", 2, List.of("otp"), List.of(),
                null, null, null, "dec-1", "v1", null, null);
    }

    // ── status ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an outage answers 503, not 403 — the status alone distinguishes it")
    void outageIsServiceUnavailable() {
        assertThat(TrustChallengeResponder.statusFor(TrustChallengeDecision.TEMPORARILY_UNAVAILABLE))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("step-up answers 401 — it is an authentication concern, not authorization")
    void stepUpIsUnauthorized() {
        assertThat(TrustChallengeResponder.statusFor(TrustChallengeDecision.STEP_UP_REQUIRED))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(TrustChallengeResponder.statusFor(TrustChallengeDecision.REAUTHENTICATION_REQUIRED))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a real refusal is still 403 — the statuses are not all different for its own sake")
    void denyIsForbidden() {
        assertThat(TrustChallengeResponder.statusFor(TrustChallengeDecision.DENY))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(TrustChallengeResponder.statusFor(TrustChallengeDecision.CONSENT_REQUIRED))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── continuation minting ──────────────────────────────────────────────────

    @Test
    @DisplayName("an actionable challenge mints a continuation so the journey can resume")
    void actionableMintsContinuation() {
        assertThatThrownBy(() -> responder.enforce(
                TrustDecisionResult.of(outcome(TrustChallengeDecision.CONSENT_REQUIRED, "CONSENT_REQUIRED")),
                "/patients/1/notes", "denied"))
                .isInstanceOf(TrustChallengeException.class)
                .satisfies(e -> assertThat(((TrustChallengeException) e).outcome().continuationReference())
                        .isEqualTo("cont-xyz"));

        verify(store).saveContinuation("/patients/1/notes", "CONSENT_REQUIRED",
                Map.of("reasonCode", "CONSENT_REQUIRED", "requiredAction", "ACT",
                       "decisionId", "dec-1", "requiredAssurance", "2"));
    }

    @Test
    @DisplayName("a flat DENY mints NOTHING — a resume token for a journey with no next step")
    void denyMintsNoContinuation() {
        // Handing the shell a continuation for an unappealable refusal is what produces a
        // fabricated "try again" affordance that can never succeed.
        assertThatThrownBy(() -> responder.enforce(
                TrustDecisionResult.of(outcome(TrustChallengeDecision.DENY, "NO_MATCHING_RULE")),
                "/patients/1/notes", "denied"))
                .isInstanceOf(TrustChallengeException.class);

        verify(store, never()).saveContinuation(anyString(), any(), any());
    }

    @Test
    @DisplayName("an outage mints nothing either — there is no journey state to preserve")
    void outageMintsNoContinuation() {
        assertThatThrownBy(() -> responder.enforce(
                TrustDecisionResult.unavailable("AUTHZ_UNREACHABLE"), "/x", "unavailable"))
                .isInstanceOf(TrustChallengeException.class);

        verify(store, never()).saveContinuation(anyString(), any(), any());
    }

    @Test
    @DisplayName("a continuation store failure does not turn a refusal into a 500")
    void continuationFailureIsNotFatal() {
        // The continuation is an affordance; the refusal is the control. Losing Redis must not
        // convert a well-explained 403 into an opaque server error.
        when(store.saveContinuation(anyString(), any(), any()))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> responder.enforce(
                TrustDecisionResult.of(outcome(TrustChallengeDecision.CONSENT_REQUIRED, "CONSENT_REQUIRED")),
                "/x", "denied"))
                .isInstanceOf(TrustChallengeException.class)
                .satisfies(e -> assertThat(((TrustChallengeException) e).outcome().continuationReference())
                        .isNull());
    }

    @Test
    @DisplayName("ALLOW returns normally — a responder that always threw would pass the rest")
    void allowDoesNotThrow() {
        responder.enforce(TrustDecisionResult.of(
                TrustChallengeOutcome.allow("dec-1", "v1")), "/x", "should not throw");
    }

    // ── the HTTP envelope ─────────────────────────────────────────────────────

    @Test
    @DisplayName("the rendered body carries the whole outcome, and no internal prose")
    void envelopeCarriesTheOutcome() {
        TrustChallengeOutcome o = outcome(TrustChallengeDecision.CONSENT_REQUIRED, "CONSENT_REQUIRED")
                .withContinuationReference("cont-xyz");
        var response = handler.handleTrustChallenge(
                new TrustChallengeException(o, "Tshepo PDP denied telemedicine read"),
                new MockHttpServletRequest("GET", "/internal/v1/teleconsults"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("CONSENT_REQUIRED");
        assertThat(error.get("message"))
                .as("the governance service's log sentence names internal topology and helps nobody")
                .isEqualTo("trust.k");
        assertThat(error.get("message").toString()).doesNotContain("Tshepo PDP");

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) error.get("details");
        assertThat(details.get("trust_challenge")).isEqualTo(o);
        assertThat(error).containsKeys("request_id", "correlation_id");
    }

    @Test
    @DisplayName("the envelope's status follows the decision, not a constant")
    void envelopeStatusFollowsDecision() {
        var response = handler.handleTrustChallenge(
                new TrustChallengeException(
                        outcome(TrustChallengeDecision.TEMPORARILY_UNAVAILABLE, "AUTHZ_UNREACHABLE"),
                        "unavailable"),
                new MockHttpServletRequest("GET", "/internal/v1/teleconsults"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
