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

    /**
     * Governed clinical write via the gateway's PEP-enforced forward endpoint
     * ({@code POST /internal/v1/gateway/forward}). Unlike {@link #createResource}, this hits the
     * route fhir-gateway actually serves: consent PEP → route lookup → FhirForwarder → audit +
     * outbox. {@code payload} is the FHIR resource serialised as a JSON string (the ForwardRequest
     * contract). Returns the gateway's {@code {status,data}} envelope.
     *
     * @param resourceType FHIR resource type (must be in the gateway's clinical set to route)
     * @param operation    e.g. CREATE / UPDATE
     * @param payloadJson  the FHIR resource as a JSON string
     */
    public JsonNode forward(String tenantId, String correlationId, String actorId,
                            String resourceType, String operation, String payloadJson,
                            String subjectCpid, String purposeOfUse) {
        String url = baseUrl + "/internal/v1/gateway/forward";
        java.util.Map<String, Object> req = new java.util.LinkedHashMap<>();
        req.put("tenantId", tenantId);
        req.put("correlationId", correlationId);
        req.put("actorId", actorId);
        req.put("sourceIp", null);
        req.put("resourceType", resourceType);
        req.put("operation", operation);
        req.put("payload", payloadJson);
        req.put("subjectCpid", subjectCpid);
        req.put("purposeOfUse", purposeOfUse);
        log.info("FHIR Gateway: forwarding {} {} (subject={})", operation, resourceType, subjectCpid);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(req), JsonNode.class);
        return response.getBody();
    }
}
