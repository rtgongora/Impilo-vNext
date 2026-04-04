package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for the COSTA (Costing Engine) sovereign service.
 *
 * <p>Provides access to billing, tariff, and payment resources.
 * COSTA manages the canonical bill lifecycle including costing,
 * charging rules, invoicing, and payment intent creation.</p>
 */
@Component
public class CostaServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CostaServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CostaServiceClient(RestTemplate serviceRestTemplate,
                              ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.costaBaseUrl();
    }

    /**
     * List bills (paginated, tenant-scoped via trust headers).
     *
     * @param page   zero-based page number
     * @param size   page size
     * @param status optional bill status filter (e.g. DRAFT, FINAL)
     * @return paginated bill list from COSTA
     */
    public JsonNode listBills(int page, int size, String status) {
        StringBuilder url = new StringBuilder(baseUrl + "/costa/v1/bills?page=" + page + "&size=" + size);
        if (status != null && !status.isBlank()) {
            url.append("&status=").append(status);
        }
        log.info("COSTA: Listing bills page={}, size={}, status={}", page, size, status);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single bill with its lines and parties.
     */
    public JsonNode getBill(String billId) {
        String url = baseUrl + "/costa/v1/bills/" + billId;
        log.info("COSTA: Getting bill={}", billId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * List tariffs (paginated, tenant-scoped via trust headers).
     */
    public JsonNode listTariffs(int page, int size) {
        String url = baseUrl + "/costa/v1/tariffs?page=" + page + "&size=" + size;
        log.info("COSTA: Listing tariffs page={}, size={}", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * List payments (paginated, tenant-scoped via trust headers).
     */
    public JsonNode listPayments(int page, int size) {
        String url = baseUrl + "/costa/v1/payments?page=" + page + "&size=" + size;
        log.info("COSTA: Listing payments page={}, size={}", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * List payments for a specific bill.
     */
    public JsonNode getBillPayments(String billId) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/payments";
        log.info("COSTA: Getting payments for bill={}", billId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get full audit trail for a bill.
     */
    public JsonNode getBillAudit(String billId) {
        String url = baseUrl + "/costa/v1/audit/bill/" + billId;
        log.info("COSTA: Getting audit trail for bill={}", billId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
