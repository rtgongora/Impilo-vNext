package zw.gov.mohcc.impilo.pct.core.telemedicine;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Telemedicine session provider that runs provider↔patient consults on the same canonical
 * self-hosted media path as Khuluma calls + live meetings: rtc-gateway-service → LiveKit. Maps the
 * neutral {@link TelemedicineSessionProvider.SessionProvisioningRequest} onto rtc-gateway's
 * {@code POST /internal/v1/rtc/sessions} contract (incl. the consentReference the media consent gate
 * requires for VIDEO/AUDIO) and returns the real LiveKit room URL + token. Activate by setting
 * {@code pct.telemedicine.provider.default-provider-type=RTC_GATEWAY} (or per-request providerType).
 */
@Component
public class PctRtcGatewaySessionProvider implements TelemedicineSessionProvider {

    private static final Logger log = LoggerFactory.getLogger(PctRtcGatewaySessionProvider.class);
    private static final String SESSIONS = "/internal/v1/rtc/sessions";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String podId;

    public PctRtcGatewaySessionProvider(
            @Value("${pct.telemedicine.provider.rtc-gateway.base-url:http://localhost:8195}") String baseUrl,
            @Value("${POD_ID:national-spine}") String podId) {
        this(baseUrl, podId, new RestTemplate());
    }

    PctRtcGatewaySessionProvider(String baseUrl, String podId, RestTemplate restTemplate) {
        this.baseUrl = baseUrl;
        this.podId = podId;
        this.restTemplate = restTemplate;
    }

    @Override
    public String providerType() {
        return "RTC_GATEWAY";
    }

    @Override
    public SessionProvisioningResult provision(SessionProvisioningRequest request) {
        Map<String, Object> attributes = request.attributes() == null ? Map.of() : request.attributes();
        String sessionId = text(attributes.get("sessionId"));
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        String providerId = request.providerId();
        boolean providerInitiated = providerId != null && !providerId.isBlank();
        String identity = providerInitiated ? providerId : request.patientCpid();

        Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("identity", identity);
        participant.put("displayName", identity);
        participant.put("role", providerInitiated ? "PROVIDER" : "PATIENT");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", request.tenantId().toString());
        body.put("sessionId", sessionId);
        body.put("patientId", request.patientCpid());
        body.put("providerId", providerId);
        body.put("facilityId", request.facilityId());
        body.put("referralId", request.referralId());
        body.put("encounterId", request.encounterId());
        body.put("sessionType", request.sessionType() != null ? request.sessionType() : "VIDEO");
        body.put("purposeOfUse", text(attributes.getOrDefault("purposeOfUse", "TREATMENT")));
        body.put("consentReference", consentReference(attributes, request, sessionId));
        body.put("participant", participant);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-ID", request.tenantId().toString());
        headers.set("X-Pod-ID", podId);
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        if (providerInitiated) {
            headers.set("X-Actor-ID", providerId);
            headers.set("X-Actor-Type", "PROVIDER");
        }

        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(baseUrl + SESSIONS, new HttpEntity<>(body, headers), JsonNode.class);
        JsonNode payload = response.getBody();
        if (payload != null && payload.has("data") && payload.get("data").isObject()) {
            payload = payload.get("data");
        }
        if (payload == null) {
            throw new IllegalStateException("rtc-gateway returned an empty session response");
        }
        String roomUrl = text(payload.get("roomUrl"));
        String accessToken = text(payload.get("accessToken"));
        if (roomUrl == null || accessToken == null) {
            throw new IllegalStateException("rtc-gateway did not return roomUrl + accessToken");
        }
        String channel = "rtc-gateway:" + text(payload.get("id"));
        log.info("PCT teleconsult provisioned via rtc-gateway [session={}, provider={}]", sessionId, providerId);
        return new SessionProvisioningResult(providerType(), channel, roomUrl, accessToken);
    }

    @Override
    public void end(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String id = sessionId.startsWith("rtc-gateway:") ? sessionId.substring("rtc-gateway:".length()) : sessionId;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Pod-ID", podId);
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Correlation-ID", UUID.randomUUID().toString());
            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            restTemplate.postForEntity(baseUrl + SESSIONS + "/" + id + "/end",
                    new HttpEntity<>(Map.of(), headers), JsonNode.class);
        } catch (RuntimeException ex) {
            log.warn("rtc-gateway session end failed for {}: {}", id, ex.getMessage());
        }
    }

    /** Media consent gate requires a non-blank reference for VIDEO/AUDIO; derive one from clinical context. */
    private static String consentReference(Map<String, Object> attributes, SessionProvisioningRequest request,
                                           String sessionId) {
        String explicit = text(attributes.get("consentReference"));
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        if (request.encounterId() != null && !request.encounterId().isBlank()) {
            return "pct-encounter:" + request.encounterId();
        }
        return "pct-session:" + sessionId;
    }

    private static String text(Object value) {
        if (value == null) return null;
        if (value instanceof JsonNode node) {
            return node.isNull() ? null : node.asText();
        }
        String s = value.toString();
        return s.isBlank() ? null : s;
    }
}
