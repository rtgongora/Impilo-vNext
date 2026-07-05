package zw.gov.mohcc.impilo.learning.integration;

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
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound client for Impilo Live Fundo webinar scheduling (doctrine §3).
 */
@Service
public class LiveSessionIntegration {

    private static final Logger log = LoggerFactory.getLogger(LiveSessionIntegration.class);
    private static final String API = "/internal/v1/live/fundo-webinars";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean enabled;

    public LiveSessionIntegration(
            RestTemplate learningRestTemplate,
            @Value("${learning.integration.live.base-url:http://localhost:8380}") String baseUrl,
            @Value("${learning.integration.live.enabled:true}") boolean enabled) {
        this.restTemplate = learningRestTemplate;
        this.baseUrl = trimSlash(baseUrl);
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<Map<String, Object>> scheduleFundoWebinar(Map<String, Object> request) {
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
                log.info("Impilo Live Fundo webinar scheduled for course {}", request.get("courseId"));
                return Optional.of(response.getBody());
            }
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Impilo Live unavailable for Fundo webinar: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Map<String, Object> buildFundoWebinarPayload(
            UUID courseId,
            String title,
            String description,
            Object startTime,
            Object endTime,
            List<String> trainerIds,
            Boolean cpdEnabled,
            Object cpdPoints,
            Integer attendanceThresholdMinutes,
            Boolean replayAllowed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("courseId", courseId);
        body.put("title", title);
        body.put("description", description);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        body.put("trainerIds", trainerIds);
        body.put("learnerAudience", "ROLE_BASED");
        body.put("cpdEnabled", cpdEnabled != null && cpdEnabled);
        body.put("cpdPoints", cpdPoints);
        body.put("attendanceThresholdMinutes", attendanceThresholdMinutes != null ? attendanceThresholdMinutes : 30);
        body.put("replayAllowed", replayAllowed == null || replayAllowed);
        body.put("visibility", "ROLE_BASED");
        return body;
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // live-service enforces the full v1.1 companion header set; this call
        // originates service-side, so the mandatory identifiers are synthesized
        // when the inbound context can't supply them.
        headers.set(CompanionHeaders.POD_ID, "learning-service");
        headers.set(CompanionHeaders.REQUEST_ID, java.util.UUID.randomUUID().toString());
        headers.set("Idempotency-Key", "learning-live-schedule:" + java.util.UUID.randomUUID());
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) {
            if (ctx.tenantId() != null) {
                headers.set(CompanionHeaders.TENANT_ID, ctx.tenantId());
            }
            if (ctx.principal() != null && ctx.principal().getName() != null) {
                headers.set(CompanionHeaders.ACTOR_ID, ctx.principal().getName());
            }
            headers.set(CompanionHeaders.CORRELATION_ID,
                    ctx.correlationId() != null ? ctx.correlationId()
                            : java.util.UUID.randomUUID().toString());
        } else {
            headers.set(CompanionHeaders.CORRELATION_ID, java.util.UUID.randomUUID().toString());
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
