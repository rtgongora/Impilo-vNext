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

    // ── Groups & Discussions (legacy compatibility) ──────────────────
    // These routes are the legacy "social groups" surface backed by the new
    // social subdomain. They keep BFF#CommunityController happy while the
    // first-class /community/social/** routes (below) take over.

    public JsonNode listGroups(String category, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/community/social/groups");
        if (category != null) builder.queryParam("category", category);
        log.info("COMMUNITY: listGroups operation (social)");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.encode().build().toUri(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode createGroup(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/community/social/groups";
        log.info("COMMUNITY: createGroup operation (social)");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode joinGroup(String groupId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/community/social/groups/" + groupId + "/join";
        log.info("COMMUNITY: joinGroup operation [groupId={}]", groupId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request == null ? Map.of() : request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listPosts(String groupId, int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/community/social/feed")
                .queryParam("scope", "group")
                .queryParam("scopeId", groupId)
                .queryParam("limit", size)
                .queryParam("offset", page * size)
                .encode()
                .toUriString();
        log.info("COMMUNITY: listPosts operation [groupId={}]", groupId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createPost(String groupId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/community/social/posts";
        log.info("COMMUNITY: createPost operation [groupId={}]", groupId);
        Map<String, Object> body = new java.util.HashMap<>(request == null ? Map.of() : request);
        Object scope = body.get("scope");
        if (!(scope instanceof Map<?, ?>)) {
            scope = new java.util.HashMap<String, Object>();
        }
        ((Map<String, Object>) scope).put("groupId", groupId);
        body.put("scope", scope);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── First-class social subdomain ──────────────────────────────────

    public JsonNode getFeed(String scope, String scopeId, int limit, int offset) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/community/social/feed")
                .queryParam("scope", scope == null ? "home" : scope)
                .queryParam("limit", limit)
                .queryParam("offset", offset);
        if (scopeId != null) builder.queryParam("scopeId", scopeId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.encode().build().toUri(), JsonNode.class);
        return rawBody(response);
    }

    public JsonNode createSocialPost(Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/posts", body, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode getSocialPost(String id) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/internal/v1/community/social/posts/" + id, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode reactToPost(String id, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/posts/" + id + "/reactions", body, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode unreactPost(String id) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/internal/v1/community/social/posts/" + id + "/reactions",
                HttpMethod.DELETE, HttpEntity.EMPTY, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode listPostComments(String id) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/internal/v1/community/social/posts/" + id + "/comments", JsonNode.class);
        return rawBody(response);
    }

    public JsonNode addComment(String id, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/posts/" + id + "/comments", body, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode bookmarkPost(String id) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/posts/" + id + "/bookmark", Map.of(), JsonNode.class);
        return rawBody(response);
    }

    public JsonNode unbookmarkPost(String id) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/internal/v1/community/social/posts/" + id + "/bookmark",
                HttpMethod.DELETE, HttpEntity.EMPTY, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode reportPost(String id, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/posts/" + id + "/report", body, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode listSocialCommunities(String category, String q) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/community/social/communities");
        if (category != null) builder.queryParam("category", category);
        if (q != null) builder.queryParam("q", q);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.encode().build().toUri(), JsonNode.class);
        return rawBody(response);
    }

    public JsonNode getSocialCommunity(String id) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/internal/v1/community/social/communities/" + id, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode createSocialCommunity(Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/communities", body, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode joinSocialCommunity(String id) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/communities/" + id + "/join", Map.of(), JsonNode.class);
        return rawBody(response);
    }

    public JsonNode leaveSocialCommunity(String id) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/communities/" + id + "/leave", Map.of(), JsonNode.class);
        return rawBody(response);
    }

    public JsonNode listSocialGroups(String communityId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/community/social/groups");
        if (communityId != null) builder.queryParam("communityId", communityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.encode().build().toUri(), JsonNode.class);
        return rawBody(response);
    }

    public JsonNode getSocialGroup(String id) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/internal/v1/community/social/groups/" + id, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode listSocialPages(String kind) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/community/social/pages");
        if (kind != null) builder.queryParam("kind", kind);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.encode().build().toUri(), JsonNode.class);
        return rawBody(response);
    }

    public JsonNode getSocialPage(String id) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/internal/v1/community/social/pages/" + id, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode createSocialPage(Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/pages", body, JsonNode.class);
        return rawBody(response);
    }

    public JsonNode followSocialPage(String id) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/pages/" + id + "/follow", Map.of(), JsonNode.class);
        return rawBody(response);
    }

    public JsonNode socialSuggestions() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/internal/v1/community/social/suggestions", JsonNode.class);
        return rawBody(response);
    }

    public JsonNode moderationQueue() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                baseUrl + "/internal/v1/community/social/moderation/queue", JsonNode.class);
        return rawBody(response);
    }

    public JsonNode resolveModeration(String caseId, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/internal/v1/community/social/moderation/" + caseId + "/resolve", body, JsonNode.class);
        return rawBody(response);
    }

    private JsonNode rawBody(ResponseEntity<JsonNode> response) {
        return response.getBody();
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
