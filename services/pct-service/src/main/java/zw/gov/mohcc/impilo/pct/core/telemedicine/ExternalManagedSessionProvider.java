package zw.gov.mohcc.impilo.pct.core.telemedicine;

import com.fasterxml.jackson.databind.JsonNode;
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
 * External managed media adapter.
 */
@Component
public class ExternalManagedSessionProvider implements TelemedicineSessionProvider {

    private final TelemedicineProviderProperties providerProperties;
    private final RestTemplate restTemplate;

    public ExternalManagedSessionProvider(TelemedicineProviderProperties providerProperties) {
        this.providerProperties = providerProperties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String providerType() {
        return "EXTERNAL_MANAGED";
    }

    @Override
    public SessionProvisioningResult provision(SessionProvisioningRequest request) {
        if (!providerProperties.getExternal().isEnabled()) {
            throw new IllegalStateException("External telemedicine provider is disabled");
        }
        String baseUrl = trim(providerProperties.getExternal().getBaseUrl());
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("External telemedicine provider baseUrl is not configured");
        }

        String path = providerProperties.getExternal().getSessionPath();
        String url = baseUrl + (path == null || path.isBlank() ? "/v1/sessions" : path);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = trim(providerProperties.getExternal().getApiKey());
        if (!apiKey.isBlank()) {
            headers.set("X-API-Key", apiKey);
        }
        headers.set("X-Tenant-Id", request.tenantId().toString());
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());

        Map<String, Object> attributes = request.attributes() == null ? Map.of() : request.attributes();
        String sessionId = text(attributes.get("sessionId"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", request.tenantId().toString());
        body.put("sessionId", sessionId);
        body.put("patientId", request.patientCpid());
        body.put("providerId", request.providerId());
        body.put("facilityId", request.facilityId());
        body.put("sessionType", request.sessionType());
        body.put("referralId", request.referralId());
        body.put("encounterId", request.encounterId());
        body.put("purposeOfUse", text(attributes.getOrDefault("purposeOfUse", "TREATMENT")));
        body.put("consentReference", text(attributes.get("consentReference")));
        body.put("participant", participant(request));
        body.put("attributes", attributes);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), JsonNode.class);
        JsonNode payload = response.getBody();
        if (payload == null) {
            throw new IllegalStateException("External telemedicine provider returned empty response");
        }
        if (payload.has("data") && payload.get("data").isObject()) {
            payload = payload.get("data");
        }
        String roomUrl = first(payload, "roomUrl", "joinUrl", "sessionUrl");
        if (roomUrl == null || roomUrl.isBlank()) {
            throw new IllegalStateException("External telemedicine provider did not return roomUrl");
        }
        String token = first(payload, "token", "accessToken", "sessionToken");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("External telemedicine provider did not return access token");
        }
        String channel = first(payload, "channel", "channelId");
        return new SessionProvisioningResult(providerType(), channel == null ? "external-managed" : channel, roomUrl, token);
    }

    @Override
    public void end(String sessionId) {
        if (!providerProperties.getExternal().isEnabled() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String baseUrl = trim(providerProperties.getExternal().getBaseUrl());
        if (baseUrl.isBlank()) {
            return;
        }
        String path = providerProperties.getExternal().getSessionPath();
        String normalizedPath = path == null || path.isBlank() ? "/v1/sessions" : path;
        String url = baseUrl + normalizedPath + "/" + sessionId + "/end";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = trim(providerProperties.getExternal().getApiKey());
        if (!apiKey.isBlank()) {
            headers.set("X-API-Key", apiKey);
        }
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
        restTemplate.postForEntity(url, new HttpEntity<>(Map.of("reason", "PCT session ended"), headers), JsonNode.class);
    }

    private Map<String, Object> participant(SessionProvisioningRequest request) {
        String providerId = trim(request.providerId());
        String identity = providerId.isBlank() ? request.patientCpid() : providerId;
        String role = providerId.isBlank() ? "PATIENT" : "PROVIDER";
        Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("identity", identity);
        participant.put("displayName", identity);
        participant.put("role", role);
        return participant;
    }

    private String first(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.hasNonNull(key)) {
                String value = node.get(key).asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
