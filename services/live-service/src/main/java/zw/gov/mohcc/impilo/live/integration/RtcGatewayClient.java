package zw.gov.mohcc.impilo.live.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.Map;

@Component
public class RtcGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(RtcGatewayClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RtcGatewayClient(RestTemplate restTemplate,
                            @Value("${live.integrations.rtc-gateway-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = IntegrationHttpSupport.trimSlash(baseUrl);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> provisionSession(TrustContext ctx, Map<String, Object> request) {
        return exchange(ctx, HttpMethod.POST, baseUrl + "/internal/v1/rtc/sessions", request);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSession(TrustContext ctx, String sessionId) {
        return exchange(ctx, HttpMethod.GET, baseUrl + "/internal/v1/rtc/sessions/" + sessionId, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> issueToken(TrustContext ctx, String sessionId, Map<String, Object> request) {
        return exchange(ctx, HttpMethod.POST,
                baseUrl + "/internal/v1/rtc/sessions/" + sessionId + "/participants/token", request);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> endSession(TrustContext ctx, String sessionId) {
        return exchange(ctx, HttpMethod.POST, baseUrl + "/internal/v1/rtc/sessions/" + sessionId + "/end", Map.of());
    }

    /** Media-quality aggregates from rtc webhook telemetry (participant_stats). */
    public Map<String, Object> getSessionStats(TrustContext ctx, String sessionId) {
        return exchange(ctx, HttpMethod.GET,
                baseUrl + "/internal/v1/rtc/sessions/" + sessionId + "/stats", null);
    }

    /** Start a recording egress for the session ({startedBy, startedByRole, layout?}). */
    public Map<String, Object> startRecording(TrustContext ctx, String sessionId, Map<String, Object> request) {
        return exchange(ctx, HttpMethod.POST,
                baseUrl + "/internal/v1/rtc/sessions/" + sessionId + "/recording/start", request);
    }

    /** Stop the active recording egress for the session. */
    public Map<String, Object> stopRecording(TrustContext ctx, String sessionId) {
        return exchange(ctx, HttpMethod.POST,
                baseUrl + "/internal/v1/rtc/sessions/" + sessionId + "/recording/stop", Map.of());
    }

    /** List recording artifacts ({egressId, status, storageBucket, storageKey, documentObjectId, ...}). */
    public Map<String, Object> listRecordings(TrustContext ctx, String sessionId) {
        return exchange(ctx, HttpMethod.GET,
                baseUrl + "/internal/v1/rtc/sessions/" + sessionId + "/recordings", null);
    }

    private Map<String, Object> exchange(TrustContext ctx, HttpMethod method, String url, Object body) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, method,
                    new HttpEntity<>(body, IntegrationHttpSupport.trustHeaders(ctx)),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception ex) {
            log.warn("RtcGatewayClient {} {} failed: {}", method, url, ex.getMessage());
            return Map.of();
        }
    }
}
