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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", request.tenantId().toString());
        body.put("patientId", request.patientCpid());
        body.put("providerId", request.providerId());
        body.put("facilityId", request.facilityId());
        body.put("sessionType", request.sessionType());
        body.put("referralId", request.referralId());
        body.put("encounterId", request.encounterId());
        body.put("attributes", request.attributes());

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), JsonNode.class);
        JsonNode payload = response.getBody();
        if (payload == null) {
            throw new IllegalStateException("External telemedicine provider returned empty response");
        }
        String roomUrl = first(payload, "roomUrl", "joinUrl", "sessionUrl");
        if (roomUrl == null || roomUrl.isBlank()) {
            roomUrl = "https://external.telecare.impilo/room/" + UUID.randomUUID();
        }
        String token = first(payload, "token", "accessToken", "sessionToken");
        if (token == null || token.isBlank()) {
            token = UUID.randomUUID().toString().replace("-", "");
        }
        return new SessionProvisioningResult(providerType(), "external-managed", roomUrl, token);
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
}
