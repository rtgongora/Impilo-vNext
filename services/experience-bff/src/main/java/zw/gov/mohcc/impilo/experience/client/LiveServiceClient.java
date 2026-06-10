package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for live-service — live events, registrations, room, interactions,
 * moderation, attendance, certificates, analytics, and discovery.
 */
@Component
public class LiveServiceClient {

    private static final Logger log = LoggerFactory.getLogger(LiveServiceClient.class);
    private static final String API = "/internal/v1/live";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public LiveServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.liveBaseUrl();
    }

    // ── Events ───────────────────────────────────────────────────────

    public JsonNode createEvent(Map<String, Object> body) {
        return post(baseUrl + API + "/events", body, "createEvent");
    }

    public JsonNode getEvent(String eventId) {
        return get(baseUrl + API + "/events/" + eventId, "getEvent");
    }

    public JsonNode updateEvent(String eventId, Map<String, Object> body) {
        return exchange(HttpMethod.PUT, baseUrl + API + "/events/" + eventId, body, "updateEvent");
    }

    public JsonNode scheduleEvent(String eventId) {
        return post(baseUrl + API + "/events/" + eventId + "/schedule", Map.of(), "scheduleEvent");
    }

    public JsonNode cancelEvent(String eventId) {
        return post(baseUrl + API + "/events/" + eventId + "/cancel", Map.of(), "cancelEvent");
    }

    public JsonNode listEvents(String status) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/events");
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        return get(builder.toUriString(), "listEvents");
    }

    // ── Registrations ────────────────────────────────────────────────

    public JsonNode register(String eventId, Map<String, Object> body) {
        return post(baseUrl + API + "/registrations/events/" + eventId, body, "register");
    }

    public JsonNode cancelRegistration(String eventId, String participantId) {
        return delete(baseUrl + API + "/registrations/events/" + eventId
                + "/participants/" + participantId, "cancelRegistration");
    }

    public JsonNode saveRegistration(String eventId, String participantId, boolean saved) {
        String url = UriComponentsBuilder.fromHttpUrl(
                        baseUrl + API + "/registrations/events/" + eventId
                                + "/participants/" + participantId + "/saved")
                .queryParam("saved", saved)
                .toUriString();
        return exchange(HttpMethod.PUT, url, Map.of(), "saveRegistration");
    }

    public JsonNode listRegistrationsForEvent(String eventId) {
        return get(baseUrl + API + "/registrations/events/" + eventId, "listRegistrationsForEvent");
    }

    public JsonNode listRegistrationsForParticipant(String participantId) {
        return get(baseUrl + API + "/registrations/participants/" + participantId,
                "listRegistrationsForParticipant");
    }

    // ── Room ─────────────────────────────────────────────────────────

    public JsonNode joinRoom(String eventId, Map<String, Object> body) {
        return post(baseUrl + API + "/room/" + eventId + "/join", body, "joinRoom");
    }

    public JsonNode roomToken(String eventId, Map<String, Object> body) {
        return post(baseUrl + API + "/room/" + eventId + "/token", body, "roomToken");
    }

    public JsonNode startRoom(String eventId) {
        return post(baseUrl + API + "/room/" + eventId + "/start", Map.of(), "startRoom");
    }

    public JsonNode endRoom(String eventId) {
        return post(baseUrl + API + "/room/" + eventId + "/end", Map.of(), "endRoom");
    }

    public JsonNode recordRoom(String eventId) {
        return post(baseUrl + API + "/room/" + eventId + "/record", Map.of(), "recordRoom");
    }

    public JsonNode mediaHealth(String eventId) {
        return get(baseUrl + API + "/room/" + eventId + "/media-health", "mediaHealth");
    }

    public JsonNode getReplay(String eventId) {
        return get(baseUrl + API + "/room/" + eventId + "/replay", "getReplay");
    }

    public JsonNode processReplay(String eventId) {
        return post(baseUrl + API + "/room/" + eventId + "/process-replay", Map.of(), "processReplay");
    }

    public JsonNode publishReplay(String eventId) {
        return post(baseUrl + API + "/room/" + eventId + "/publish-replay", Map.of(), "publishReplay");
    }

    // ── Interactions ─────────────────────────────────────────────────

    public JsonNode submitQuestion(String eventId, Map<String, Object> body) {
        return post(baseUrl + API + "/interactions/" + eventId + "/questions", body, "submitQuestion");
    }

    public JsonNode listQuestions(String eventId) {
        return get(baseUrl + API + "/interactions/" + eventId + "/questions", "listQuestions");
    }

    public JsonNode upvoteQuestion(String eventId, String questionId) {
        return post(baseUrl + API + "/interactions/" + eventId + "/questions/" + questionId + "/upvote",
                Map.of(), "upvoteQuestion");
    }

    public JsonNode postChat(String eventId, Map<String, Object> body) {
        return post(baseUrl + API + "/interactions/" + eventId + "/chat", body, "postChat");
    }

    public JsonNode listChat(String eventId) {
        return get(baseUrl + API + "/interactions/" + eventId + "/chat", "listChat");
    }

    public JsonNode createPoll(String eventId, Map<String, Object> body) {
        return post(baseUrl + API + "/interactions/" + eventId + "/polls", body, "createPoll");
    }

    public JsonNode activatePoll(String eventId, String pollId) {
        return post(baseUrl + API + "/interactions/" + eventId + "/polls/" + pollId + "/activate",
                Map.of(), "activatePoll");
    }

    public JsonNode respondPoll(String eventId, String pollId, Map<String, Object> body) {
        return post(baseUrl + API + "/interactions/" + eventId + "/polls/" + pollId + "/respond",
                body, "respondPoll");
    }

    public JsonNode listPolls(String eventId) {
        return get(baseUrl + API + "/interactions/" + eventId + "/polls", "listPolls");
    }

    public JsonNode submitFeedback(String eventId, Map<String, Object> body) {
        return post(baseUrl + API + "/interactions/" + eventId + "/feedback", body, "submitFeedback");
    }

    public JsonNode listResources(String eventId) {
        return get(baseUrl + API + "/interactions/" + eventId + "/resources", "listResources");
    }

    public JsonNode addResource(String eventId, Map<String, Object> body) {
        return post(baseUrl + API + "/interactions/" + eventId + "/resources", body, "addResource");
    }

    // ── Moderation ───────────────────────────────────────────────────

    public JsonNode moderateChat(String eventId, String messageId, Map<String, Object> body) {
        return post(baseUrl + API + "/moderation/" + eventId + "/chat/" + messageId, body, "moderateChat");
    }

    public JsonNode moderateQuestion(String eventId, String questionId, Map<String, Object> body) {
        return post(baseUrl + API + "/moderation/" + eventId + "/questions/" + questionId, body,
                "moderateQuestion");
    }

    public JsonNode setChatEnabled(String eventId, Map<String, Object> body) {
        return exchange(HttpMethod.PUT, baseUrl + API + "/moderation/" + eventId + "/chat-enabled", body,
                "setChatEnabled");
    }

    public JsonNode setQnaEnabled(String eventId, Map<String, Object> body) {
        return exchange(HttpMethod.PUT, baseUrl + API + "/moderation/" + eventId + "/qna-enabled", body,
                "setQnaEnabled");
    }

    // ── Attendance ───────────────────────────────────────────────────

    public JsonNode attendanceJoin(String eventId, Map<String, Object> body) {
        return post(baseUrl + API + "/attendance/" + eventId + "/join", body, "attendanceJoin");
    }

    public JsonNode attendanceLeave(String eventId, Map<String, Object> body) {
        return post(baseUrl + API + "/attendance/" + eventId + "/leave", body, "attendanceLeave");
    }

    public JsonNode trackMinutes(String eventId, String participantId, Map<String, Object> body) {
        return exchange(HttpMethod.PUT,
                baseUrl + API + "/attendance/" + eventId + "/participants/" + participantId + "/minutes",
                body, "trackMinutes");
    }

    public JsonNode listAttendance(String eventId) {
        return get(baseUrl + API + "/attendance/" + eventId, "listAttendance");
    }

    public JsonNode getAttendance(String eventId, String participantId) {
        return get(baseUrl + API + "/attendance/" + eventId + "/participants/" + participantId, "getAttendance");
    }

    // ── Certificates ─────────────────────────────────────────────────

    public JsonNode issueAttendanceCertificate(String eventId, String participantId) {
        return post(baseUrl + API + "/certificates/" + eventId + "/attendance/" + participantId,
                Map.of(), "issueAttendanceCertificate");
    }

    public JsonNode issueCpdCertificate(String eventId, String participantId) {
        return post(baseUrl + API + "/certificates/" + eventId + "/cpd/" + participantId,
                Map.of(), "issueCpdCertificate");
    }

    public JsonNode listCertificates(String eventId) {
        return get(baseUrl + API + "/certificates/" + eventId, "listCertificates");
    }

    public JsonNode verifyCertificate(String verificationCode) {
        return get(baseUrl + API + "/certificates/verify/" + verificationCode, "verifyCertificate");
    }

    // ── Analytics ────────────────────────────────────────────────────

    public JsonNode captureSnapshot(String eventId) {
        return post(baseUrl + API + "/analytics/" + eventId + "/snapshot", Map.of(), "captureSnapshot");
    }

    public JsonNode analyticsDashboard(String eventId) {
        return get(baseUrl + API + "/analytics/" + eventId + "/dashboard", "analyticsDashboard");
    }

    public JsonNode analyticsHistory(String eventId, String metricName) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/analytics/" + eventId + "/history")
                .queryParam("metricName", metricName)
                .toUriString();
        return get(url, "analyticsHistory");
    }

    // ── Discovery ────────────────────────────────────────────────────

    public JsonNode discoverByContext(String contextType) {
        return get(baseUrl + API + "/discover/context/" + contextType, "discoverByContext");
    }

    public JsonNode discoverByFacility(String facilityId) {
        return get(baseUrl + API + "/discover/facility/" + facilityId, "discoverByFacility");
    }

    public JsonNode discoverByRole(String userId, String role) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/discover/role")
                .queryParam("userId", userId)
                .queryParam("role", role)
                .toUriString();
        return get(url, "discoverByRole");
    }

    // ── Typed scheduling (doctrine §7 — owning services) ───────────

    public JsonNode scheduleClinicalSession(Map<String, Object> body) {
        return post(baseUrl + API + "/clinical-sessions", body, "scheduleClinicalSession");
    }

    public JsonNode scheduleFundoWebinar(Map<String, Object> body) {
        return post(baseUrl + API + "/fundo-webinars", body, "scheduleFundoWebinar");
    }

    public JsonNode schedulePublicBroadcast(Map<String, Object> body) {
        return post(baseUrl + API + "/public-broadcasts", body, "schedulePublicBroadcast");
    }

    private JsonNode get(String url, String operation) {
        log.debug("LIVE {}: GET {}", operation, url);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    private JsonNode post(String url, Map<String, Object> body, String operation) {
        log.debug("LIVE {}: POST {}", operation, url);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    private JsonNode delete(String url, String operation) {
        log.debug("LIVE {}: DELETE {}", operation, url);
        return restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, JsonNode.class).getBody();
    }

    private JsonNode exchange(HttpMethod method, String url, Map<String, Object> body, String operation) {
        log.debug("LIVE {}: {} {}", operation, method, url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, method, entity, JsonNode.class).getBody();
    }
}
