package zw.gov.mohcc.impilo.experience.trust.shadow;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The observer's containment properties, stated as failures rather than as comments.
 *
 * <p>Every assertion here corresponds to a way this could quietly become harmful: dispatching when
 * it was supposed to be off, dispatching with no configured secret, carrying a servlet request into
 * async work, rendering a bearer into a string, or changing a request it was only supposed to
 * watch.</p>
 */
class ShadowObservationContainmentTest {

    private ShadowObservationProperties props;
    private ShadowProbeDispatcher dispatcher;
    private ShadowObservationFilter filter;

    @BeforeEach
    void setUp() {
        props = new ShadowObservationProperties();
        props.setEnabled(true);
        props.setProbeKey("a-high-entropy-probe-secret-value");
        props.setAsyncSampleRate(1.0);
        props.setCanarySampleRate(0.0);
        dispatcher = mock(ShadowProbeDispatcher.class);
        filter = new ShadowObservationFilter(props, dispatcher);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithJwt() {
        Jwt jwt = Jwt.withTokenValue("a.user.bearer")
                .header("alg", "RS256")
                .claim("sub", "actor-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));
    }

    private static MockHttpServletRequest gatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/facilities");
        request.addHeader("x-decision", "ALLOW");
        request.addHeader("x-actor-type", "CITIZEN");
        request.addHeader("x-actor-id", "actor-1");
        return request;
    }

    private void run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    // ── The gates ───────────────────────────────────────────────────────────────────────────

    @Test
    void disabledDispatchesNothingAndReadsNoToken() throws Exception {
        props.setEnabled(false);
        authenticateWithJwt();

        run(gatedRequest());

        verify(dispatcher, never()).dispatchAsync(any());
        verify(dispatcher, never()).runInlineCanary(any());
        // Not even a skip is counted: while disabled the observer does not look at the request.
        verify(dispatcher, never()).markSkippedNoBearer();
    }

    /**
     * A blank secret must mean "do not dispatch", not "dispatch and let the PDP refuse". The PDP
     * fails closed on a blank key, so a blank-key BFF would generate a 403 per request forever while
     * appearing to be measuring something.
     */
    @Test
    void aBlankProbeKeyDispatchesNothing() throws Exception {
        props.setProbeKey("   ");
        authenticateWithJwt();

        run(gatedRequest());

        verify(dispatcher, never()).dispatchAsync(any());
    }

    /**
     * These carry a full, eligible-looking request — authenticated JWT and an {@code x-decision}
     * header — so the ONLY thing that can stop a dispatch is the bypass list itself. Without that,
     * the test passes because the request lacks a verdict and proves nothing about the list.
     */
    @Test
    void routesEnvoyDoesNotGateAreNotObserved() throws Exception {
        authenticateWithJwt();

        for (String uri : List.of("/internal/v1/auth/oidc/session",
                "/internal/v1/public/facilities", "/actuator/health")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            request.addHeader("x-decision", "ALLOW");
            request.addHeader("x-actor-id", "actor-1");
            run(request);
        }

        verify(dispatcher, never()).dispatchAsync(any());
        verify(dispatcher, never()).markSkippedNoVerdict();
    }

    @Test
    void anUnauthenticatedRequestHasNoRolesToRevealAndIsCountedAsSuch() throws Exception {
        run(gatedRequest());

        verify(dispatcher, never()).dispatchAsync(any());
        verify(dispatcher).markSkippedNoBearer();
    }

    /** A non-JWT authentication is not a bearer. Reading it as one would shadow a token that isn't. */
    @Test
    void aNonJwtAuthenticationIsNotTreatedAsABearer() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("someone", "credentials", "ROLE_CLINICIAN"));

        run(gatedRequest());

        verify(dispatcher, never()).dispatchAsync(any());
        verify(dispatcher).markSkippedNoBearer();
    }

    /**
     * Without {@code x-decision} there is no production verdict to compare against. Recording such a
     * request would add rows that cannot express a verdict delta and would dilute the very ratio the
     * report is computed from.
     */
    @Test
    void aRequestWithNoProductionVerdictIsSkippedAndCounted() throws Exception {
        authenticateWithJwt();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/facilities");

        run(request);

        verify(dispatcher, never()).dispatchAsync(any());
        verify(dispatcher).markSkippedNoVerdict();
    }

    @Test
    void anEligibleRequestIsObservedExactlyOnce() throws Exception {
        authenticateWithJwt();

        run(gatedRequest());

        verify(dispatcher).markEligible();
        verify(dispatcher).dispatchAsync(any());
        verify(dispatcher, never()).runInlineCanary(any());
    }

    /** Canary and census are exclusive: both would bill one decision twice and log it twice. */
    @Test
    void theCanaryAndTheCensusAreMutuallyExclusive() throws Exception {
        props.setCanarySampleRate(1.0);
        authenticateWithJwt();

        run(gatedRequest());

        verify(dispatcher).runInlineCanary(any());
        verify(dispatcher, never()).dispatchAsync(any());
    }

    // ── It observes; it does not participate ────────────────────────────────────────────────

    @Test
    void theChainRunsExactlyOnceAndTheResponseIsUntouched() throws Exception {
        authenticateWithJwt();
        MockHttpServletRequest request = gatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest(), "the chain must receive the original request");
        assertEquals(200, response.getStatus());
        assertTrue(response.getHeaderNames().isEmpty(),
                "an observer that sets a response header is no longer an observer");
    }

    // ── The envelope ────────────────────────────────────────────────────────────────────────

    /**
     * The structural guarantee, checked structurally.
     *
     * <p>A servlet container recycles the request object once the response commits; async work
     * reading it then sees another user's headers, and under the default {@code
     * recycledFacades=false} that read succeeds and returns the wrong data instead of throwing. No
     * amount of care at the call site survives a later edit — so the envelope is asserted to be
     * incapable of carrying a servlet type at all.</p>
     */
    @Test
    void nothingServletTypedCanCrossTheAsyncBoundary() {
        for (RecordComponent component : ShadowRequestEnvelope.class.getRecordComponents()) {
            String type = component.getGenericType().getTypeName();
            assertFalse(type.contains("jakarta.servlet") || type.contains("javax.servlet"),
                    "ShadowRequestEnvelope." + component.getName()
                            + " is servlet-typed (" + type + ") — it would be read after recycling");
        }
        assertFalse(HttpServletRequest.class.isAssignableFrom(ShadowRequestEnvelope.class));
        assertFalse(HttpServletResponse.class.isAssignableFrom(ShadowRequestEnvelope.class));
    }

    @Test
    void theEnvelopeNeverRendersTheBearer() {
        ShadowRequestEnvelope envelope = new ShadowRequestEnvelope(
                "GET", "/internal/v1/facilities", "ALLOW", "CITIZEN",
                Map.of("actorId", "actor-1"), "a.user.bearer", System.nanoTime());

        String rendered = envelope.toString();

        assertFalse(rendered.contains("a.user.bearer"),
                "a DTO landing in an exception message is how tokens actually leak");
        assertTrue(rendered.contains("<redacted>"));
    }

    @Test
    void theEnvelopeIsDeeplyImmutableOnceBuilt() {
        Map<String, String> source = new HashMap<>();
        source.put("actorId", "actor-1");
        ShadowRequestEnvelope envelope = new ShadowRequestEnvelope(
                "GET", "/x", "ALLOW", "CITIZEN", source, "b", System.nanoTime());

        source.put("actorId", "actor-2");
        source.put("facilityId", "smuggled-after-publication");

        assertEquals("actor-1", envelope.trustContext().get("actorId"),
                "the envelope must not track a map the request thread still holds");
        assertFalse(envelope.trustContext().containsKey("facilityId"));
        assertThrows(UnsupportedOperationException.class,
                () -> envelope.trustContext().put("actorId", "actor-3"));
    }

    /** Absent headers stay absent: "" and missing are different inputs to PolicyEngine. */
    @Test
    void absentTrustHeadersAreOmittedRatherThanBlank() throws Exception {
        authenticateWithJwt();
        MockHttpServletRequest request = gatedRequest();
        request.addHeader("x-facility-id", "   ");

        run(request);

        ArgumentCaptor<ShadowRequestEnvelope> captor =
                ArgumentCaptor.forClass(ShadowRequestEnvelope.class);
        verify(dispatcher).dispatchAsync(captor.capture());
        assertFalse(captor.getValue().trustContext().containsKey("facilityId"),
                "a blank header must be omitted, not passed as \"\" — PolicyEngine treats them differently");
        assertEquals("actor-1", captor.getValue().trustContext().get("actorId"));
        assertEquals("ALLOW", captor.getValue().prodVerdict());
        assertEquals("CITIZEN", captor.getValue().prodActorType());
    }
}
