package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;

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
     * Create a bill draft for an encounter.
     *
     * @param encounterId the BFF encounter ID
     * @param billType    bill type (ENCOUNTER, ESTIMATE, MARKETPLACE, OUTREACH)
     * @return the created bill draft from COSTA, or null on failure
     */
    public JsonNode createBillDraft(String encounterId, String billType) {
        String url = baseUrl + "/costa/v1/bills/draft";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("encounterId", encounterId);
        if (billType != null) body.put("billType", billType);

        log.info("COSTA: Creating bill draft for encounter={}, type={}", encounterId, billType);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Submit a bill for approval (DRAFT/ACCUMULATING → APPROVAL_PENDING).
     */
    public JsonNode submitForApproval(String billId) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/submit-approval";
        log.info("COSTA: Submitting bill {} for approval", billId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Approve a bill (APPROVAL_PENDING → APPROVED).
     */
    public JsonNode approveBill(String billId, String note) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/approve";
        Map<String, Object> body = new LinkedHashMap<>();
        if (note != null) body.put("note", note);
        log.info("COSTA: Approving bill {}", billId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Finalize a bill (APPROVED → FINAL).
     */
    public JsonNode finalizeBill(String billId) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/finalize";
        log.info("COSTA: Finalizing bill {}", billId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Issue an invoice for a finalized bill.
     */
    public JsonNode issueInvoice(String billId) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/issue-invoice";
        log.info("COSTA: Issuing invoice for bill {}", billId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Create a payment intent for a finalized bill.
     *
     * @param billId      the COSTA bill ID
     * @param paymentType FULL, DEPOSIT, REMAINDER, THIRD_PARTY, or REMITTANCE
     * @param amount      payment amount
     * @return the created PaymentEntity from COSTA
     */
    public JsonNode createPaymentIntent(String billId, String paymentType, String amount) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/create-payment-intent";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentType", paymentType);
        body.put("amount", amount);
        log.info("COSTA: Creating payment intent for bill={}, type={}, amount={}", billId, paymentType, amount);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Cancel a PENDING payment for a bill.
     */
    public JsonNode cancelPayment(String billId, String paymentId) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/payments/" + paymentId + "/cancel";
        log.info("COSTA: Cancelling payment {} for bill {}", paymentId, billId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Create a refund for a bill.
     */
    public JsonNode createRefund(String billId, String amount, String reason,
                                  String reasonCode, String refundType) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/refund";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("reason", reason);
        if (reasonCode != null) body.put("reasonCode", reasonCode);
        if (refundType != null) body.put("refundType", refundType);
        log.info("COSTA: Creating refund for bill={}, amount={}", billId, amount);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * List refunds for a specific bill.
     */
    public JsonNode getBillRefunds(String billId) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/refunds";
        log.info("COSTA: Getting refunds for bill={}", billId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
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
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/costa/v1/bills")
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        log.info("COSTA: Listing bills page={}, size={}, status={}", page, size, status);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
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
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/costa/v1/tariffs")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        log.info("COSTA: Listing tariffs page={}, size={}", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * List payments (paginated, tenant-scoped via trust headers).
     */
    public JsonNode listPayments(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/costa/v1/payments")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
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
     * List claims (paginated, tenant-scoped via trust headers).
     */
    public JsonNode listClaims(int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/costa/v1/claims")
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        log.info("COSTA: Listing claims page={}, size={}", page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single claim detail.
     */
    public JsonNode getClaim(String claimId) {
        String url = baseUrl + "/costa/v1/claims/" + claimId;
        log.info("COSTA: Getting claim={}", claimId);
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
