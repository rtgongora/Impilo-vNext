package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the Community sovereign service (units, outreach visits, CHW assignments).
 *
 * <p>Trust headers are forwarded by the RestTemplate interceptor in
 * {@link zw.gov.mohcc.impilo.experience.config.ServiceClientConfig}.</p>
 */
@Component
public class CommunityServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CommunityServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public CommunityServiceClient(RestTemplate serviceRestTemplate,
                                  ServiceClientConfig.ServiceEndpoints endpoints,
                                  ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.communityBaseUrl();
        this.objectMapper = objectMapper;
    }

    public JsonNode listUnits() {
        String url = baseUrl + "/internal/v1/community/units";
        log.info("COMMUNITY: listUnits operation");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getUnit(String id) {
        String url = baseUrl + "/internal/v1/community/units/" + id;
        log.info("COMMUNITY: getUnit operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createUnit(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/community/units";
        log.info("COMMUNITY: createUnit operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listVisits(String unitId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/community/visits")
                .queryParam("unitId", unitId)
                .encode()
                .toUriString();
        log.info("COMMUNITY: listVisits operation [unitId={}]", unitId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createVisit(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/community/visits";
        log.info("COMMUNITY: createVisit operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode startVisit(String id) {
        String url = baseUrl + "/internal/v1/community/visits/" + id + "/start";
        log.info("COMMUNITY: startVisit operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode completeVisit(String id, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/community/visits/" + id + "/complete";
        log.info("COMMUNITY: completeVisit operation [id={}]", id);
        HttpEntity<?> entity = request == null ? HttpEntity.EMPTY : new HttpEntity<>(request);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listAssignments(String unitId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/community/assignments")
                .queryParam("unitId", unitId)
                .encode()
                .toUriString();
        log.info("COMMUNITY: listAssignments operation [unitId={}]", unitId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createAssignment(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/community/assignments";
        log.info("COMMUNITY: createAssignment operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
