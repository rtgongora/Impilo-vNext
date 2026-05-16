package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the Campaigns sovereign service.
 */
@Component
public class CampaignsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CampaignsServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CampaignsServiceClient(RestTemplate serviceRestTemplate,
                                  ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.campaignsBaseUrl();
    }

    public JsonNode listCampaigns(int page, int size) {
        String url = baseUrl + "/internal/v1/campaigns?page=" + page + "&size=" + size;
        log.info("Campaigns: list [page={}, size={}]", page, size);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getCampaign(long id) {
        String url = baseUrl + "/internal/v1/campaigns/" + id;
        log.info("Campaigns: get id={}", id);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createCampaign(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/campaigns";
        log.info("Campaigns: create [name={}, type={}]", request.get("name"), request.get("campaignType"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode closeCampaign(long id) {
        String url = baseUrl + "/internal/v1/campaigns/" + id + "/close";
        log.info("Campaigns: close id={}", id);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode enroll(long campaignId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/campaigns/" + campaignId + "/enroll";
        log.info("Campaigns: enroll campaignId={}", campaignId);
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode dispatch(long campaignId) {
        String url = baseUrl + "/internal/v1/campaigns/" + campaignId + "/dispatch";
        log.info("Campaigns: dispatch campaignId={}", campaignId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode requestReview(long campaignId) {
        String url = baseUrl + "/internal/v1/campaigns/" + campaignId + "/review";
        log.info("Campaigns: requestReview campaignId={}", campaignId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    public JsonNode reopenCampaign(long campaignId) {
        String url = baseUrl + "/internal/v1/campaigns/" + campaignId + "/reopen";
        log.info("Campaigns: reopen campaignId={}", campaignId);
        return extractData(restTemplate.postForEntity(url, null, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
