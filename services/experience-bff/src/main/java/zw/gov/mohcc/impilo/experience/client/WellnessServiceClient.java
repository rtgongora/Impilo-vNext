package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
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
 * HTTP client for the wellness sovereign service (port 8161).
 * Consumer-grade wellness: programs, enrollments, challenges, goals.
 */
@Component
public class WellnessServiceClient {

    private static final Logger log = LoggerFactory.getLogger(WellnessServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public WellnessServiceClient(RestTemplate serviceRestTemplate,
                                 ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.wellnessBaseUrl();
    }

    // ── Programs ─────────────────────────────────────────────────

    public JsonNode listPrograms() {
        String url = baseUrl + "/internal/v1/wellness/programs";
        log.debug("Wellness: listPrograms");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getProgram(String id) {
        String url = baseUrl + "/internal/v1/wellness/programs/" + id;
        log.debug("Wellness: getProgram id={}", id);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    // ── Enrollments ──────────────────────────────────────────────

    public JsonNode enrollInProgram(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/wellness/enrollments";
        log.info("Wellness: enroll [programId={}, participantId={}]", body.get("programId"), body.get("participantId"));
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listEnrollments(String participantId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/enrollments");
        if (participantId != null && !participantId.isBlank()) {
            // Canonical SIMBA query key; participantId accepted as compatibility alias in BFF.
            b.queryParam("person_cpid", participantId);
        }
        String url = b.toUriString();
        log.debug("Wellness: listEnrollments [participantId={}]", participantId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    // ── Challenges ───────────────────────────────────────────────

    public JsonNode listChallenges() {
        String url = baseUrl + "/internal/v1/wellness/challenges";
        log.debug("Wellness: listChallenges");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode joinChallenge(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/wellness/challenges/" + id + "/join";
        log.info("Wellness: joinChallenge id={}", id);
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    // ── Goals ────────────────────────────────────────────────────

    public JsonNode listGoals(String participantId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/goals");
        if (participantId != null && !participantId.isBlank()) {
            // Canonical SIMBA query key; participantId accepted as compatibility alias in BFF.
            b.queryParam("person_cpid", participantId);
        }
        String url = b.toUriString();
        log.debug("Wellness: listGoals [participantId={}]", participantId);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createGoal(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/wellness/goals";
        log.info("Wellness: createGoal [type={}]", body.get("type"));
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode updateGoal(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/wellness/goals/" + id;
        log.info("Wellness: updateGoal id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
