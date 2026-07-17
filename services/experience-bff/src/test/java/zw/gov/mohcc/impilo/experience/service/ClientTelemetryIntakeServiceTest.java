package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ObservabilityServiceClient;
import zw.gov.mohcc.impilo.experience.config.OrchestrationBacklogEndpoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Abuse-control + allow-list tests for the anonymous client-telemetry ingest lane: server-side
 * field allow-listing, per-field/batch caps, drop-on-missing-identifiers, and FAIL-CLOSED rate
 * limiting when Redis is unavailable (telemetry is not life-safety — mirrors feedback).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientTelemetryIntakeServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private CapturingObservabilityClient observability;
    private ClientTelemetryIntakeService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redis.getExpire(anyString())).thenReturn(60L);
        observability = new CapturingObservabilityClient();
        service = new ClientTelemetryIntakeService(redis, observability);
    }

    private static Map<String, Object> event(String route, String code) {
        Map<String, Object> e = new HashMap<>();
        e.put("route", route);
        e.put("code", code);
        return e;
    }

    private static Map<String, Object> batch(Object... events) {
        Map<String, Object> body = new HashMap<>();
        body.put("events", new ArrayList<>(List.of(events)));
        return body;
    }

    @Test
    void happyPath_forwardsOnlyAllowListedFields() {
        Map<String, Object> e = event("/welcome", "OK");
        e.put("correlationId", "corr-1");
        e.put("requestId", "req-1");
        e.put("status", 200);
        e.put("at", "2026-07-17T10:00:00Z");
        e.put("secretCookie", "should-be-stripped");
        e.put("userAgent", "should-be-stripped");

        int accepted = service.accept(batch(e), "41.1.2.3");

        assertThat(accepted).isEqualTo(1);
        JsonNode forwarded = observability.lastBatch.path("events").path(0);
        assertThat(forwarded.get("route").asText()).isEqualTo("/welcome");
        assertThat(forwarded.get("code").asText()).isEqualTo("OK");
        assertThat(forwarded.get("status").asInt()).isEqualTo(200);
        // Anything outside the allow-list is dropped server-side.
        assertThat(forwarded.has("secretCookie")).isFalse();
        assertThat(forwarded.has("userAgent")).isFalse();
    }

    @Test
    void eventsMissingRouteOrCode_areDropped() {
        Map<String, Object> noCode = new HashMap<>();
        noCode.put("route", "/x");
        Map<String, Object> noRoute = new HashMap<>();
        noRoute.put("code", "OK");

        int accepted = service.accept(batch(noCode, noRoute, event("/ok", "OK")), "41.1.2.3");

        assertThat(accepted).isEqualTo(1);
        assertThat(observability.lastBatch.path("events")).hasSize(1);
    }

    @Test
    void overlongFields_areTruncatedAndBadTimestampDropped() {
        Map<String, Object> e = event("/" + "a".repeat(500), "b".repeat(200));
        e.put("at", "not-a-timestamp");
        e.put("status", "not-an-int");

        service.accept(batch(e), "41.1.2.3");

        JsonNode forwarded = observability.lastBatch.path("events").path(0);
        assertThat(forwarded.get("route").asText().length())
                .isEqualTo(ClientTelemetryIntakeService.MAX_ROUTE_CHARS);
        assertThat(forwarded.get("code").asText().length())
                .isEqualTo(ClientTelemetryIntakeService.MAX_CODE_CHARS);
        assertThat(forwarded.has("at")).isFalse();       // unparseable time never trusted
        assertThat(forwarded.has("status")).isFalse();   // non-int coerced away
    }

    @Test
    void batchOverCap_isRejected() {
        List<Object> tooMany = new ArrayList<>();
        for (int i = 0; i <= ClientTelemetryIntakeService.MAX_EVENTS_PER_REQUEST; i++) {
            tooMany.add(event("/r" + i, "OK"));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("events", tooMany);

        assertThatThrownBy(() -> service.accept(body, "41.1.2.3"))
                .isInstanceOf(ClientTelemetryIntakeService.TelemetryValidationException.class);
        assertThat(observability.calls).isZero();
    }

    @Test
    void redisDown_failsClosed() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.accept(batch(event("/ok", "OK")), "41.1.2.3"))
                .isInstanceOf(ClientTelemetryIntakeService.TelemetryRateLimitedException.class);
        assertThat(observability.calls).isZero();
    }

    @Test
    void rateLimitBreach_isRateLimited() {
        when(valueOps.increment(anyString()))
                .thenReturn((long) ClientTelemetryIntakeService.MAX_REQUESTS_PER_IP + 1);

        assertThatThrownBy(() -> service.accept(batch(event("/ok", "OK")), "41.1.2.3"))
                .isInstanceOf(ClientTelemetryIntakeService.TelemetryRateLimitedException.class);
        assertThat(observability.calls).isZero();
    }

    /** Captures the forwarded batch without any real HTTP. */
    private static final class CapturingObservabilityClient extends ObservabilityServiceClient {
        JsonNode lastBatch;
        int calls;

        CapturingObservabilityClient() {
            super(new RestTemplate(), endpoints());
        }

        private static OrchestrationBacklogEndpoints endpoints() {
            OrchestrationBacklogEndpoints e = new OrchestrationBacklogEndpoints();
            e.setObservabilityBaseUrl("http://localhost:8211");
            return e;
        }

        @Override
        public void postClientEvents(JsonNode batch) {
            calls++;
            lastBatch = batch;
        }
    }
}
