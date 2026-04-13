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

import java.util.UUID;

/**
 * HTTP client for {@code inventory-service} (stock ledger, on-hand, counts, requisitions, reconcile).
 */
@Component
public class InventoryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InventoryServiceClient(RestTemplate serviceRestTemplate,
                                  ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.inventoryBaseUrl();
    }

    public JsonNode getOnHand(UUID facilityId, UUID storeId, UUID binId, String itemCode, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/onhand")
                .queryParam("facilityId", facilityId)
                .queryParam("page", page)
                .queryParam("size", size);
        if (storeId != null) {
            b.queryParam("storeId", storeId);
        }
        if (binId != null) {
            b.queryParam("binId", binId);
        }
        if (itemCode != null && !itemCode.isBlank()) {
            b.queryParam("itemCode", itemCode);
        }
        return getJson(b.toUriString());
    }

    public JsonNode getNearExpiry(UUID facilityId, int days) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/onhand/near-expiry")
                .queryParam("facilityId", facilityId)
                .queryParam("days", days)
                .toUriString();
        return getJson(url);
    }

    public JsonNode getStockouts(UUID facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/onhand/stockouts")
                .queryParam("facilityId", facilityId)
                .toUriString();
        return getJson(url);
    }

    public JsonNode getLedger(UUID facilityId, UUID storeId, String itemCode, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/ledger")
                .queryParam("facilityId", facilityId)
                .queryParam("page", page)
                .queryParam("size", size);
        if (storeId != null) {
            b.queryParam("storeId", storeId);
        }
        if (itemCode != null && !itemCode.isBlank()) {
            b.queryParam("itemCode", itemCode);
        }
        return getJson(b.toUriString());
    }

    public JsonNode postLedger(String subPath, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/ledger/" + subPath, body);
    }

    public JsonNode getItem(String itemCode) {
        return getJson(baseUrl + "/v1/items/" + itemCode);
    }

    public JsonNode lookupBarcode(String code) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/items/lookup-by-barcode")
                .queryParam("code", code)
                .toUriString();
        return getJson(url);
    }

    public JsonNode createItem(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/items", body);
    }

    public JsonNode getReconcilePending(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/reconcile/pending")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        return getJson(url);
    }

    public JsonNode resolveReconcile(UUID id, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/reconcile/" + id + "/resolve", body);
    }

    public JsonNode createCountSession(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/counts", body);
    }

    public JsonNode startCount(UUID id) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/counts/" + id + "/start", null);
    }

    public JsonNode updateCountLine(UUID sessionId, UUID lineId, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/counts/" + sessionId + "/lines/" + lineId, body);
    }

    public JsonNode submitCount(UUID id) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/counts/" + id + "/submit", null);
    }

    public JsonNode approveCount(UUID id, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/counts/" + id + "/approve", body);
    }

    public JsonNode rejectCount(UUID id, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/counts/" + id + "/reject", body);
    }

    public JsonNode getCountSession(UUID id) {
        return getJson(baseUrl + "/v1/counts/" + id);
    }

    public JsonNode createRequisition(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/requisitions", body);
    }

    public JsonNode submitRequisition(UUID id) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/requisitions/" + id + "/submit", null);
    }

    public JsonNode approveRequisition(UUID id, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/requisitions/" + id + "/approve", body);
    }

    public JsonNode rejectRequisition(UUID id, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/requisitions/" + id + "/reject", body);
    }

    public JsonNode fulfillRequisition(UUID id, JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/requisitions/" + id + "/fulfill", body);
    }

    // ── Handover (used by mobile dispatch surfaces) ─────────────────

    public JsonNode listHandovers(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/handover")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        return getJson(url);
    }

    public JsonNode startHandover(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/handover/start", body);
    }

    public JsonNode signIncomingHandover(UUID id) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/handover/" + id + "/sign-incoming", null);
    }

    private JsonNode getJson(String url) {
        log.debug("Inventory GET {}", url);
        ResponseEntity<JsonNode> r = restTemplate.exchange(url, HttpMethod.GET, emptyEntity(), JsonNode.class);
        return r.getBody();
    }

    private JsonNode exchangeJson(HttpMethod method, String url, JsonNode body) {
        log.debug("Inventory {} {}", method, url);
        HttpEntity<?> entity;
        if (body == null) {
            entity = new HttpEntity<>(null, null);
        } else {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            entity = new HttpEntity<>(body, headers);
        }
        ResponseEntity<JsonNode> r = restTemplate.exchange(url, method, entity, JsonNode.class);
        return r.getBody();
    }

    private static HttpEntity<Void> emptyEntity() {
        return new HttpEntity<>(null, null);
    }
}
