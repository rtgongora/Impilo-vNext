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
import java.util.UUID;

/**
 * HTTP client for support-service (ticketing, escalation, knowledge base).
 */
@Component
public class SupportServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SupportServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SupportServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.supportBaseUrl();
    }

    public JsonNode listTickets(String status) {
        return listTickets(status, null, null, null, 0, 50);
    }

    public JsonNode listTickets(String status, String priority, String category, String assigneeRef, int cursor, int limit) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/support/tickets")
                .queryParam("cursor", cursor)
                .queryParam("limit", limit);
        if (status != null && !status.isBlank()) b.queryParam("status", status);
        if (priority != null && !priority.isBlank()) b.queryParam("priority", priority);
        if (category != null && !category.isBlank()) b.queryParam("category", category);
        if (assigneeRef != null && !assigneeRef.isBlank()) b.queryParam("assigneeRef", assigneeRef);
        java.net.URI url = b.encode().build().toUri();
        log.debug("Support: listTickets");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getTicket(String id) {
        String url = baseUrl + "/internal/v1/support/tickets/" + id;
        log.debug("Support: getTicket id={}", id);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createTicket(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/support/tickets";
        log.info("Support: createTicket");
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode addComment(String ticketId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/support/tickets/" + ticketId + "/comments";
        log.info("Support: addComment ticketId={}", ticketId);
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode updateTicketStatus(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/support/tickets/" + id + "/status";
        log.info("Support: updateTicketStatus id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateTicket(UUID ticketId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/support/tickets/" + ticketId;
        log.info("Support: updateTicket {}", ticketId);
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode escalateTicket(UUID ticketId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/support/tickets/" + ticketId + "/escalate";
        log.info("Support: escalateTicket {}", ticketId);
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode searchArticles(String query) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/support/articles");
        if (query != null && !query.isBlank()) {
            b.queryParam("q", query);
        }
        java.net.URI url = b.encode().build().toUri();
        log.debug("Support: searchArticles");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listArticles(String category, String status, int cursor, int limit) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/support/articles")
                .queryParam("cursor", cursor)
                .queryParam("limit", limit);
        if (category != null && !category.isBlank()) b.queryParam("category", category);
        if (status != null && !status.isBlank()) b.queryParam("status", status);
        java.net.URI url = b.encode().build().toUri();
        log.debug("Support: listArticles");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    // ── Support context (A3 / Phase C resolver) ────────────────────────────

    /** Every ACTIVE scoped support-team posting a person holds (support-context, not tickets). */
    public JsonNode listSupportAssignmentsForPerson(String personHealthId) {
        String url = baseUrl + "/internal/v1/support/context/assignments/by-person/" + personHealthId;
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.warn("Support: listSupportAssignmentsForPerson failed for {}: {}", personHealthId, e.getMessage());
            return null;
        }
    }

    public JsonNode getSupportTeam(String teamId) {
        String url = baseUrl + "/internal/v1/support/context/teams/" + teamId;
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.warn("Support: getSupportTeam failed for {}: {}", teamId, e.getMessage());
            return null;
        }
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        JsonNode body = response.getBody();
        if (body != null && body.has("data")) {
            return body.get("data");
        }
        return body;
    }
}

