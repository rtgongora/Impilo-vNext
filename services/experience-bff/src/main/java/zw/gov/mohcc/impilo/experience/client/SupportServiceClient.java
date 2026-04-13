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
 * HTTP client for the support sovereign service (port 8300).
 * Provides ticket management and knowledge-base search.
 */
@Component
public class SupportServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SupportServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SupportServiceClient(RestTemplate serviceRestTemplate,
                                ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.supportBaseUrl();
    }

    public JsonNode listTickets(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/support/tickets");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        String url = b.toUriString();
        log.debug("Support: listTickets [status={}]", status);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getTicket(String id) {
        String url = baseUrl + "/internal/v1/support/tickets/" + id;
        log.debug("Support: getTicket id={}", id);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createTicket(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/support/tickets";
        log.info("Support: createTicket [subject={}]", body.get("subject"));
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode addComment(String ticketId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/support/tickets/" + ticketId + "/comments";
        log.info("Support: addComment ticketId={}", ticketId);
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode updateTicketStatus(String id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/support/tickets/" + id + "/status";
        log.info("Support: updateStatus id={} status={}", id, body.get("status"));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode searchArticles(String query) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/support/articles");
        if (query != null && !query.isBlank()) {
            b.queryParam("q", query);
        }
        String url = b.toUriString();
        log.debug("Support: searchArticles [q={}]", query);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
