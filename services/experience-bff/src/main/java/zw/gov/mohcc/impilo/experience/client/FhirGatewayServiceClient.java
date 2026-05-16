package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for fhir-gateway-service (Health OS §10: Interoperability).
 * Proxies FHIR R4 operations to the governed FHIR gateway which handles
 * routing, validation, and audit.
 */
@Component
public class FhirGatewayServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FhirGatewayServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FhirGatewayServiceClient(RestTemplate serviceRestTemplate,
                                     ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.fhirGatewayBaseUrl();
    }

    public JsonNode getCapabilityStatement() {
        String url = baseUrl + "/fhir/metadata";
        log.debug("FHIR Gateway: fetching capability statement");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode searchResource(String resourceType, String params) {
        String url = baseUrl + "/fhir/" + resourceType + "?" + params;
        log.debug("FHIR Gateway: searching {} with {}", resourceType, params);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getResource(String resourceType, String id) {
        String url = baseUrl + "/fhir/" + resourceType + "/" + id;
        log.debug("FHIR Gateway: reading {}/{}", resourceType, id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode createResource(String resourceType, JsonNode body) {
        String url = baseUrl + "/fhir/" + resourceType;
        log.info("FHIR Gateway: creating {}", resourceType);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body), JsonNode.class);
        return response.getBody();
    }
}
