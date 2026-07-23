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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP client for the SIMBA wellness sovereign service.
 *
 * <p>Club join/leave and challenge join use {@code person_cpid} from the inbound request's
 * {@code X-Subject-ID} header, falling back to {@code X-Actor-ID}, when those sovereign endpoints
 * require a body or query parameter.</p>
 *
 * <p>Trust headers are forwarded by the RestTemplate interceptor in
 * {@link zw.gov.mohcc.impilo.experience.config.ServiceClientConfig}.</p>
 */
@Component
public class SimbaServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SimbaServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public SimbaServiceClient(RestTemplate serviceRestTemplate,
                              ServiceClientConfig.ServiceEndpoints endpoints,
                              ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.simbaBaseUrl();
        this.objectMapper = objectMapper;
    }

    /** Public wellness screening-programme definitions (anonymous-safe, care-plane). Raw array. */
    public JsonNode publicScreeningProgrammes() {
        return restTemplate.getForEntity(baseUrl + "/v1/public/wellness/screening-programmes", JsonNode.class).getBody();
    }

    /** Public compute-only wellness calculator (anonymous, STATELESS — persists nothing). */
    public JsonNode publicWellnessCalculate(java.util.Map<String, Object> body) {
        return restTemplate.postForEntity(baseUrl + "/v1/public/wellness/calculate", body, JsonNode.class).getBody();
    }

    public JsonNode getWellnessProfile(String cpid) {
        String url = baseUrl + "/internal/v1/wellness/profiles/" + cpid;
        log.info("SIMBA: getWellnessProfile operation [cpid={}]", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateWellnessProfile(String cpid, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/wellness/profiles/" + cpid;
        log.info("SIMBA: updateWellnessProfile operation [cpid={}]", cpid);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listActivities(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/activities")
                .queryParam("person_cpid", cpid)
                .encode()
                .toUriString();
        log.info("SIMBA: listActivities operation [cpid={}]", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordActivity(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/wellness/activities";
        log.info("SIMBA: recordActivity operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listSleepSegments(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/sleep")
                .queryParam("person_cpid", cpid)
                .encode()
                .toUriString();
        log.info("SIMBA: listSleepSegments operation [cpid={}]", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordSleep(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/wellness/sleep";
        log.info("SIMBA: recordSleep operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listExerciseSessions(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/exercise")
                .queryParam("person_cpid", cpid)
                .encode()
                .toUriString();
        log.info("SIMBA: listExerciseSessions operation [cpid={}]", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordExercise(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/wellness/exercise";
        log.info("SIMBA: recordExercise operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listDietEntries(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/diet")
                .queryParam("person_cpid", cpid)
                .encode()
                .toUriString();
        log.info("SIMBA: listDietEntries operation [cpid={}]", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordDietEntry(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/wellness/diet";
        log.info("SIMBA: recordDietEntry operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listMoodEntries(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/mood")
                .queryParam("person_cpid", cpid)
                .encode()
                .toUriString();
        log.info("SIMBA: listMoodEntries operation [cpid={}]", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordMood(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/wellness/mood";
        log.info("SIMBA: recordMood operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listGoals(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/goals")
                .queryParam("person_cpid", cpid)
                .encode()
                .toUriString();
        log.info("SIMBA: listGoals operation [cpid={}]", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createGoal(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/wellness/goals";
        log.info("SIMBA: createGoal operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateGoalProgress(String goalId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/wellness/goals/" + goalId + "/progress";
        log.info("SIMBA: updateGoalProgress operation [goalId={}]", goalId);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listClubs() {
        String url = baseUrl + "/internal/v1/wellness/clubs";
        log.info("SIMBA: listClubs operation");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode joinClub(String clubId) {
        String url = baseUrl + "/internal/v1/wellness/clubs/" + clubId + "/join";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("person_cpid", requirePersonCpidForMembership());
        log.info("SIMBA: joinClub operation [clubId={}]", clubId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode leaveClub(String clubId) {
        String personCpid = requirePersonCpidForMembership();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/clubs/" + clubId + "/leave")
                .queryParam("person_cpid", personCpid)
                .encode()
                .toUriString();
        log.info("SIMBA: leaveClub operation [clubId={}]", clubId);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.DELETE, HttpEntity.EMPTY, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listChallenges() {
        String url = baseUrl + "/internal/v1/wellness/challenges";
        log.info("SIMBA: listChallenges operation");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode joinChallenge(String challengeId) {
        String url = baseUrl + "/internal/v1/wellness/challenges/" + challengeId + "/join";
        Map<String, Object> body = Map.of("person_cpid", requirePersonCpidForMembership());
        log.info("SIMBA: joinChallenge operation [challengeId={}]", challengeId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateChallengeProgress(String challengeId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/wellness/challenges/" + challengeId + "/progress";
        log.info("SIMBA: updateChallengeProgress operation [challengeId={}]", challengeId);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    // ── Wellness DEPTH: plans/journeys, habits, coaching, care linkage ───────────────

    public JsonNode listPlans() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/internal/v1/wellness/programs", JsonNode.class);
        return extractData(response);
    }

    public JsonNode listEnrollments(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/enrollments")
                .queryParam("person_cpid", cpid).encode().toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode enrollPlan(Map<String, Object> request) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/wellness/enrollments", request, JsonNode.class));
    }

    public JsonNode listHabitCheckIns(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/habits/check-ins")
                .queryParam("person_cpid", cpid).encode().toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode checkInHabit(Map<String, Object> request) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/wellness/habits/check-ins", request, JsonNode.class));
    }

    public JsonNode listCoaching(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/coaching/relationships")
                .queryParam("person_cpid", cpid).encode().toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listCareLinkages(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/care-linkages")
                .queryParam("person_cpid", cpid).encode().toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode routeCare(Map<String, Object> request) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/wellness/care-linkages", request, JsonNode.class));
    }

    // ── Wellness completion (assessments / follow-ups / timeline) ─────────────
    public JsonNode listAssessments(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/assessments")
                .queryParam("person_cpid", cpid).encode().toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getAssessment(String assessmentId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/internal/v1/wellness/assessments/" + assessmentId, JsonNode.class));
    }

    public JsonNode listFollowUpsForPerson(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/follow-ups")
                .queryParam("person_cpid", cpid).encode().toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listFollowUpsForProvider(String providerId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/wellness/follow-ups")
                .queryParam("provider_id", providerId).encode().toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createFollowUp(Map<String, Object> request) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/wellness/follow-ups", request, JsonNode.class));
    }

    public JsonNode recordManualReading(Map<String, Object> request) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/internal/v1/wellness/personal-data/readings/manual", request, JsonNode.class));
    }

    public JsonNode providerHealthSummary(String cpid) {
        String url = UriComponentsBuilder.fromHttpUrl(
                        baseUrl + "/internal/v1/wellness/personal-data/provider-summary")
                .queryParam("person_cpid", cpid).encode().toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private static String requirePersonCpidForMembership() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("SIMBA membership calls require an active HTTP request context");
        }
        HttpServletRequest req = attrs.getRequest();
        String subject = header(req, CompanionHeaders.SUBJECT_ID);
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        String actor = header(req, CompanionHeaders.ACTOR_ID);
        if (actor != null && !actor.isBlank()) {
            return actor;
        }
        throw new IllegalStateException(
                "SIMBA membership calls require X-Subject-ID or X-Actor-ID for person_cpid");
    }

    private static String header(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return v == null ? null : v.trim();
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
