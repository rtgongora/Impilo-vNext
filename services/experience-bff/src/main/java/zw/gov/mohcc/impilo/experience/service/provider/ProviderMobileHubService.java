package zw.gov.mohcc.impilo.experience.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.OneUiShellMobileHubClient;
import zw.gov.mohcc.impilo.experience.config.BffProviderHubsProperties;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProviderMobileHubService {

    private static final Logger log = LoggerFactory.getLogger(ProviderMobileHubService.class);

    private final BffProviderHubsProperties properties;
    private final OneUiShellMobileHubClient oneUiShellMobileHubClient;

    public ProviderMobileHubService(
            BffProviderHubsProperties properties,
            OneUiShellMobileHubClient oneUiShellMobileHubClient) {
        this.properties = properties;
        this.oneUiShellMobileHubClient = oneUiShellMobileHubClient;
    }

    public List<Map<String, Object>> sectionsForHub(String hub, List<Map<String, Object>> stub) {
        if (properties.getMode() == BffProviderHubsProperties.Mode.stub) {
            return stub;
        }
        try {
            JsonNode root = oneUiShellMobileHubClient.getProviderHub(hub);
            JsonNode sections = root != null ? root.get("sections") : null;
            if (sections != null && sections.isArray()) {
                // Preserve the JSON shape from one-ui-shell without inventing new fields.
                // Convert to Map list via Jackson's natural tree-to-Map conversion.
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) new com.fasterxml.jackson.databind.ObjectMapper()
                        .convertValue(sections, List.class);
                return list;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "one-ui-shell hub missing sections: " + hub);
        } catch (RestClientException e) {
            log.warn("Provider hub {} downstream failure: {}", hub, e.getMessage());
            if (properties.getFailurePolicy() == BffProviderHubsProperties.FailurePolicy.propagate) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "downstream unavailable: provider hub " + hub, e);
            }
            return stub;
        }
    }

    public Map<String, Object> hubEnvelope(String requestId, String correlationId, List<Map<String, Object>> sections) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", Map.of(
                "refreshed_at", OffsetDateTime.now(),
                "sections", sections
        ));
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return body;
    }
}

