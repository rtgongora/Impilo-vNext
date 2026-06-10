package zw.gov.mohcc.impilo.campaigns.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.auth.TrustHeaders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound client for Impilo Live public broadcast scheduling (doctrine §4).
 *
 * <p>Public Health / campaigns owns messaging; Impilo Live provides broadcast infrastructure.
 */
@Service
public class LiveSessionIntegration {

    private static final Logger log = LoggerFactory.getLogger(LiveSessionIntegration.class);
    private static final String API = "/internal/v1/live/public-broadcasts";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean enabled;

    public LiveSessionIntegration(
            RestTemplate restTemplate,
            @Value("${campaigns.integration.live.base-url:http://localhost:8380}") String baseUrl,
            @Value("${campaigns.integration.live.enabled:true}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.baseUrl = trimSlash(baseUrl);
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<Map<String, Object>> schedulePublicBroadcast(Map<String, Object> request) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, trustHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + API,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<>() {});
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Impilo Live public broadcast scheduled for campaign {}",
                        request.get("campaignId"));
                return Optional.of(response.getBody());
            }
            log.warn("Impilo Live returned {} for public broadcast", response.getStatusCode());
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Impilo Live unavailable for public broadcast: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Map<String, Object> buildPublicBroadcastPayload(
            String campaignId,
            String title,
            String description,
            Object startTime,
            Object endTime,
            List<String> officialSpeakerIds,
            List<String> moderatorIds,
            String audienceScope,
            Boolean emergencyBriefing,
            Boolean replayAllowed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("campaignId", campaignId);
        body.put("title", title);
        body.put("description", description);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        body.put("officialSpeakerIds", officialSpeakerIds);
        body.put("moderatorIds", moderatorIds);
        body.put("audienceScope", audienceScope != null ? audienceScope : "PUBLIC");
        body.put("commentModerationPolicy", "MODERATED");
        body.put("replayAllowed", replayAllowed == null || replayAllowed);
        body.put("emergencyBriefing", Boolean.TRUE.equals(emergencyBriefing));
        return body;
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        TrustContext ctx = TrustContextHolder.get();
        if (ctx != null) {
            if (ctx.tenantId() != null) {
                headers.set(TrustHeaders.X_TENANT_ID, ctx.tenantId().toString());
            }
            if (ctx.actorId() != null) {
                headers.set(TrustHeaders.X_ACTOR_ID, ctx.actorId());
            }
            if (ctx.correlationId() != null) {
                headers.set(TrustHeaders.X_CORRELATION_ID, ctx.correlationId().toString());
            }
        }
        return headers;
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
