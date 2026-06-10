package zw.gov.mohcc.impilo.pct.integration;

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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound client for Impilo Live typed scheduling (doctrine §7).
 *
 * <p>Telemedicine/PCT owns clinical care; Impilo Live owns the live engagement layer. This client
 * creates governed clinical sessions with consent and clinical context — never free-floating rooms.
 */
@Service
public class LiveSessionIntegration {

    private static final Logger log = LoggerFactory.getLogger(LiveSessionIntegration.class);
    private static final String API = "/internal/v1/live/clinical-sessions";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean enabled;

    public LiveSessionIntegration(
            RestTemplate restTemplate,
            @Value("${pct.integration.live.base-url:http://localhost:8380}") String baseUrl,
            @Value("${pct.integration.live.enabled:true}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.baseUrl = trimSlash(baseUrl);
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Schedule a clinical Impilo Live session linked to a telehealth encounter.
     *
     * @return scheduled live event metadata (id, status, policies) or empty if disabled/unavailable
     */
    public Optional<Map<String, Object>> scheduleClinicalSession(Map<String, Object> request) {
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
                log.info("Impilo Live clinical session scheduled for context {}",
                        request.get("clinicalContextRef"));
                return Optional.of(response.getBody());
            }
            log.warn("Impilo Live returned {} for clinical session",
                    response.getStatusCode());
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Impilo Live unavailable for clinical session: {}", ex.getMessage());
            return Optional.empty();
        }
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
            if (ctx.purposeOfUse() != null) {
                headers.set(TrustHeaders.X_PURPOSE_OF_USE, ctx.purposeOfUse());
            }
        }
        return headers;
    }

    /** Build a governed clinical scheduling payload from a telehealth session request. */
    public Map<String, Object> buildClinicalPayload(
            String clinicalContextRef,
            String clientId,
            String providerId,
            String facilityId,
            String title,
            String description,
            Object startTime,
            Object endTime,
            String consentState,
            String recordingPolicy,
            Boolean emergencyFlag) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clinicalContextRef", clinicalContextRef);
        body.put("clientId", clientId);
        body.put("providerId", providerId);
        body.put("facilityId", facilityId);
        body.put("title", title != null ? title : "Clinical telemedicine session");
        body.put("description", description);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        body.put("privacyLevel", "PATIENT_IDENTIFIABLE");
        body.put("consentState", consentState != null ? consentState : "GRANTED");
        body.put("recordingPolicy", recordingPolicy != null ? recordingPolicy : "DISABLED");
        body.put("emergencyFlag", Boolean.TRUE.equals(emergencyFlag));
        body.put("visibility", "RESTRICTED");
        return body;
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
