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
        return extractData(exchangeJson(HttpMethod.POST, baseUrl + "/v1/requisitions", body));
    }

    public JsonNode listRequisitions(UUID facilityId, int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/requisitions")
                .queryParam("facilityId", facilityId)
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        return extractData(getJsonEntity(url));
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

    public JsonNode postClinicalConsumption(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/internal/consumption/clinical", body);
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

    // ── Dura (sovereign stock brain) ────────────────────────────

    public JsonNode duraCategories(String programmeArea) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/dura/categories");
        if (programmeArea != null && !programmeArea.isBlank()) {
            b.queryParam("programmeArea", programmeArea);
        }
        return getJson(b.toUriString());
    }

    public JsonNode duraCommodities(String q, String programmeArea, Boolean controlled, Boolean coldChain) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/dura/commodities");
        if (q != null && !q.isBlank()) {
            b.queryParam("q", q);
        }
        if (programmeArea != null && !programmeArea.isBlank()) {
            b.queryParam("programmeArea", programmeArea);
        }
        if (controlled != null) {
            b.queryParam("controlled", controlled);
        }
        if (coldChain != null) {
            b.queryParam("coldChain", coldChain);
        }
        return getJson(b.toUriString());
    }

    public JsonNode duraNearExpiryBatches(int days) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/dura/batches/near-expiry")
                .queryParam("days", days)
                .toUriString();
        return getJson(url);
    }

    public JsonNode duraRecalls(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/dura/recalls");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return getJson(b.toUriString());
    }

    public JsonNode duraCreateRecall(JsonNode body) {
        return exchangeJson(HttpMethod.POST, baseUrl + "/v1/dura/recalls", body);
    }

    public JsonNode duraColdChainExcursions(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/dura/cold-chain/excursions");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return getJson(b.toUriString());
    }

    public JsonNode duraExternalSyncStates(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/dura/external-sync");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return getJson(b.toUriString());
    }

    public JsonNode duraPctAvailability(UUID facilityId, UUID storeId, String itemCode) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/dura/pct/availability")
                .queryParam("facilityId", facilityId)
                .queryParam("storeId", storeId)
                .queryParam("itemCode", itemCode)
                .toUriString();
        return getJson(url);
    }

    // ── implant lifecycle (Wave P8 §14; inventory-service ImplantController, the implant SoR).
    // Direct to inventory-service deliberately: inpatient's theatre implant proxy covers
    // record/list plus a case-side recall projection only — removal, revision and the SoR-side
    // recall trace have no inpatient path, and inpatient-service source is out of scope for
    // the SB-3 reachability wave. ──

    /** Record removal (explant) — POST /v1/internal/implants/{patientImplantId}/remove */
    public JsonNode removeImplant(String patientImplantId, JsonNode body) {
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/v1/internal/implants/" + patientImplantId + "/remove", body);
    }

    /** Record revision (explant + replacement) — POST /v1/internal/implants/{patientImplantId}/revise */
    public JsonNode reviseImplant(String patientImplantId, JsonNode body) {
        return exchangeJson(HttpMethod.POST,
                baseUrl + "/v1/internal/implants/" + patientImplantId + "/revise", body);
    }

    /** SoR recall trace — GET /v1/internal/implants/recall?udi=&lot= */
    public JsonNode traceImplantRecall(String udi, String lot) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/implants/recall");
        if (udi != null && !udi.isBlank()) b.queryParam("udi", udi);
        if (lot != null && !lot.isBlank()) b.queryParam("lot", lot);
        return extractData(getJsonEntity(b.encode().toUriString()));
    }

    private JsonNode getJson(String url) {
        return getJsonEntity(url).getBody();
    }

    private ResponseEntity<JsonNode> getJsonEntity(String url) {
        log.debug("Inventory GET {}", url);
        // URI.create prevents RestTemplate re-encoding the already-encoded builder output
        // (multi-word q= search terms otherwise arrive double-encoded and match nothing).
        return restTemplate.exchange(java.net.URI.create(url), HttpMethod.GET, emptyEntity(), JsonNode.class);
    }

    private JsonNode extractData(JsonNode body) {
        if (body != null && body.has("data")) {
            return body.get("data");
        }
        return body;
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() == null) {
            return null;
        }
        return extractData(response.getBody());
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
        return extractData(r);
    }

    private static HttpEntity<Void> emptyEntity() {
        return new HttpEntity<>(null, null);
    }
}
