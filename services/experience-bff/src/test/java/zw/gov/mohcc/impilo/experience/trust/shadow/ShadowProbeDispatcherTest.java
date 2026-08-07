package zw.gov.mohcc.impilo.experience.trust.shadow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;

/**
 * The wire contract, pinned.
 *
 * <p>The contract is fixed by agreement — {@code POST /v1/authorize/shadow}, the
 * {@code X-Impilo-Shadow-Probe-Key} header, the {@code ShadowEvaluationRequest} body — and the PDP
 * fails closed on any part of it. A silent rename here would produce 403s that look exactly like a
 * misconfigured secret, so the contract is asserted rather than trusted to a constant.</p>
 */
class ShadowProbeDispatcherTest {

    private static final String KEY = "a-high-entropy-probe-secret-value";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShadowProbeDispatcher dispatcher(ShadowObservationProperties props) {
        return new ShadowProbeDispatcher(props, "http://tshepo-authz-service:8081");
    }

    private static ShadowObservationProperties props() {
        ShadowObservationProperties props = new ShadowObservationProperties();
        props.setEnabled(true);
        props.setProbeKey(KEY);
        props.setCanaryTimeoutMillis(5000);
        return props;
    }

    private static ShadowRequestEnvelope envelope() {
        return new ShadowRequestEnvelope("GET", "/internal/v1/facilities", "ALLOW", "CITIZEN",
                Map.of("actorId", "actor-1", "workContextToken", "duty-token"),
                "a.user.bearer", System.nanoTime());
    }

    @Test
    void theProbeGoesToExactlyOneUrlCarryingExactlyOneSecret() throws Exception {
        ShadowProbeDispatcher dispatcher = dispatcher(props());
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(dispatcher.restTemplate()).build();

        server.expect(requestTo("http://tshepo-authz-service:8081/v1/authorize/shadow"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(ShadowProbeDispatcher.PROBE_KEY_HEADER, KEY))
                .andRespond(withAccepted());

        dispatcher.runInlineCanary(envelope());

        server.verify();
    }

    /**
     * The bearer travels in the body to the PDP and nowhere else. In particular it must NOT be set
     * as an {@code Authorization} header: that is the shape that would make this endpoint an
     * alternate authentication route, and the shape the reverted commit {@code 78d217bb9} would have
     * spread to ~100 domain services.
     */
    @Test
    void theBearerTravelsAsEvidenceInTheBodyNeverAsAnAuthorizationHeader() throws Exception {
        ShadowProbeDispatcher dispatcher = dispatcher(props());
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(dispatcher.restTemplate()).build();

        server.expect(requestTo("http://tshepo-authz-service:8081/v1/authorize/shadow"))
                .andExpect(request -> {
                    assertNull(request.getHeaders().getFirst("Authorization"),
                            "the shadow probe must never present the user's token as its own authority");
                    JsonNode body = MAPPER.readTree(
                            ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                                    .getBodyAsString());
                    assertEquals("a.user.bearer", body.path("bearer").asText());
                    assertEquals("GET", body.path("method").asText());
                    assertEquals("/internal/v1/facilities", body.path("path").asText());
                    assertEquals("ALLOW", body.path("prodVerdict").asText());
                    assertEquals("CITIZEN", body.path("prodActorType").asText());
                    assertEquals(ShadowProbeDispatcher.MODE_INLINE_CANARY,
                            body.path("probeMode").asText());
                    assertEquals("duty-token",
                            body.path("trustContext").path("workContextToken").asText(),
                            "the duty token must reach the PDP so it can PROVE the work mode");
                })
                .andRespond(withAccepted());

        dispatcher.runInlineCanary(envelope());

        server.verify();
    }

    /** A completed canary contributes a latency sample; the report's percentiles come from these. */
    @Test
    void aCompletedCanaryIsMeasured() {
        ShadowProbeDispatcher dispatcher = dispatcher(props());
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(dispatcher.restTemplate()).build();
        server.expect(requestTo("http://tshepo-authz-service:8081/v1/authorize/shadow"))
                .andRespond(withAccepted());

        dispatcher.runInlineCanary(envelope());

        ShadowProbeDispatcher.Snapshot s = dispatcher.snapshot();
        assertEquals(1, s.canarySamples());
        assertEquals(1, s.canaryCompleted());
        assertEquals(0, s.transportErrors());
    }

    /**
     * A PDP that refuses or is unreachable must produce a counted transport error and nothing else —
     * no exception escaping into a request thread, and no log line rendering the failed request,
     * whose body is a bearer token.
     */
    @Test
    void aRefusedProbeIsCountedAndSwallowed() {
        ShadowProbeDispatcher dispatcher = dispatcher(props());
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(dispatcher.restTemplate()).build();
        server.expect(requestTo("http://tshepo-authz-service:8081/v1/authorize/shadow"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

        dispatcher.runInlineCanary(envelope());

        ShadowProbeDispatcher.Snapshot s = dispatcher.snapshot();
        assertEquals(1, s.transportErrors());
        assertEquals(0, s.dispatched());
    }

    /**
     * Overflow must be dropped and COUNTED. A dataset that silently omits the requests made while
     * the estate was busiest looks complete and is wrong in exactly the direction that matters.
     */
    @Test
    void queueOverflowIsDroppedAndCounted() throws Exception {
        ShadowObservationProperties props = props();
        props.setAsyncPoolSize(1);
        props.setAsyncQueueCapacity(1);
        props.setConnectTimeoutMillis(50);
        props.setReadTimeoutMillis(50);
        // Nothing listens here, so each probe occupies its worker until the connect timeout.
        ShadowProbeDispatcher dispatcher = new ShadowProbeDispatcher(props, "http://127.0.0.1:1");

        for (int i = 0; i < 200; i++) {
            dispatcher.dispatchAsync(envelope());
        }

        TimeUnit.MILLISECONDS.sleep(50);
        assertTrue(dispatcher.snapshot().droppedQueueFull() > 0,
                "a bounded queue that never reports a drop is an unbounded queue in disguise");
    }

    @Test
    void percentilesUseNearestRankAndNeverInventAValue() {
        assertNull(ShadowProbeDispatcher.percentile(new int[]{}, 95));
        assertEquals(1, ShadowProbeDispatcher.percentile(new int[]{1}, 50));
        assertEquals(1, ShadowProbeDispatcher.percentile(new int[]{1}, 99));
        int[] ten = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertEquals(5, ShadowProbeDispatcher.percentile(ten, 50));
        assertEquals(10, ShadowProbeDispatcher.percentile(ten, 95));
        assertEquals(10, ShadowProbeDispatcher.percentile(ten, 99));
    }
}
