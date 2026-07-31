package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * ── Wave 2 elective theatre ── BFF proxy for the surgical funnel surfaces:
 * referral worklist, surgical waitlist and OR sessions. Stateless
 * orchestration-by-reference — forwards trust headers via the shared
 * serviceRestTemplate interceptor and returns the sovereign responses as-is.
 */
@RestController
public class TheatreSchedulingController {

    private static final Logger log = LoggerFactory.getLogger(TheatreSchedulingController.class);

    private final RestTemplate restTemplate;
    private final String schedulingBaseUrl;
    private final String referralBaseUrl;

    public TheatreSchedulingController(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.scheduling-service-base-url:http://localhost:8128}") String schedulingBaseUrl,
            @Value("${impilo.services.orchestration-backlog.referral-service-base-url:http://localhost:8399}")
            String referralBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.schedulingBaseUrl = schedulingBaseUrl;
        this.referralBaseUrl = referralBaseUrl;
    }

    // ── Surgical waitlist ────────────────────────────────────────────────────────
    @GetMapping("/internal/v1/scheduling/surgical-waitlist")
    public Object waitlist(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String sort) {
        String url = UriComponentsBuilder.fromHttpUrl(schedulingBaseUrl + "/v1/internal/scheduling/surgical-waitlist")
                .queryParamIfPresent("status", java.util.Optional.ofNullable(status))
                .queryParamIfPresent("sort", java.util.Optional.ofNullable(sort))
                .toUriString();
        return getList(url);
    }

    @PostMapping("/internal/v1/scheduling/surgical-waitlist/{id}/defer")
    public Object defer(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return post(schedulingBaseUrl + "/v1/internal/scheduling/surgical-waitlist/" + id + "/defer", body);
    }

    // ── OR sessions ──────────────────────────────────────────────────────────────
    @GetMapping("/internal/v1/scheduling/theatre-sessions")
    public Object sessions(@RequestParam(required = false) String facilityId,
                           @RequestParam(required = false) String date) {
        String url = UriComponentsBuilder.fromHttpUrl(schedulingBaseUrl + "/v1/internal/scheduling/theatre-sessions")
                .queryParamIfPresent("facilityId", java.util.Optional.ofNullable(facilityId))
                .queryParamIfPresent("date", java.util.Optional.ofNullable(date))
                .toUriString();
        return getList(url);
    }

    @GetMapping("/internal/v1/scheduling/theatre-sessions/{id}")
    public Object session(@PathVariable String id) {
        return get(schedulingBaseUrl + "/v1/internal/scheduling/theatre-sessions/" + id);
    }

    @PostMapping("/internal/v1/scheduling/theatre-sessions")
    public Object createSession(@RequestBody Map<String, Object> body) {
        return post(schedulingBaseUrl + "/v1/internal/scheduling/theatre-sessions", body);
    }

    @PostMapping("/internal/v1/scheduling/theatre-sessions/{id}/items")
    public Object addItem(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return post(schedulingBaseUrl + "/v1/internal/scheduling/theatre-sessions/" + id + "/items", body);
    }

    @PostMapping("/internal/v1/scheduling/theatre-sessions/{id}/publish")
    public Object publish(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return post(schedulingBaseUrl + "/v1/internal/scheduling/theatre-sessions/" + id + "/publish", body);
    }

    // ── Surgical referral worklist ─────────────────────────────────────────────────
    @GetMapping("/internal/v1/referrals/surgical/worklist")
    public Object referralWorklist(@RequestParam(required = false) String specialty,
                                   @RequestParam(required = false) String decision) {
        String url = UriComponentsBuilder.fromHttpUrl(referralBaseUrl + "/v1/internal/referrals/surgical/worklist")
                .queryParamIfPresent("specialty", java.util.Optional.ofNullable(specialty))
                .queryParamIfPresent("decision", java.util.Optional.ofNullable(decision))
                .toUriString();
        return getList(url);
    }

    @PostMapping("/internal/v1/referrals/surgical/{id}/decide")
    public Object decide(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return post(referralBaseUrl + "/v1/internal/referrals/surgical/" + id + "/decide", body);
    }

    // ── helpers ────────────────────────────────────────────────────────────────────
    /**
     * No swallow: an upstream failure must reach the client as a real error so the UI can
     * distinguish "empty worklist/waitlist/session list" from "service unavailable".
     */
    private Object getList(String url) {
        ResponseEntity<JsonNode> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(null), JsonNode.class);
        return resp.getBody();
    }

    private Object get(String url) {
        ResponseEntity<JsonNode> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(null), JsonNode.class);
        return resp.getBody();
    }

    private Object post(String url, Map<String, Object> body) {
        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class);
        return resp.getBody();
    }
}
