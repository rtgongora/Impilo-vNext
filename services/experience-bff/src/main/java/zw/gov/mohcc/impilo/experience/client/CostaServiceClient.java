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
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
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
    public JsonNode applyCoverage(String billId) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/apply-coverage";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    public JsonNode finalizeBill(String billId) {
        return finalizeBill(billId, false);
    }

    /**
     * Finalize an approved bill. When {@code allowUnpriced} is true, COSTA will
     * finalize a bill that still carries pending-priced (no configured tariff)
     * lines, treating them as zero — an explicit governed override, never the
     * default. Fail-closed remains the norm.
     */
    public JsonNode finalizeBill(String billId, boolean allowUnpriced) {
        String url = baseUrl + "/costa/v1/bills/" + billId + "/finalize";
        if (allowUnpriced) {
            url = UriComponentsBuilder.fromHttpUrl(url).queryParam("allowUnpriced", true).toUriString();
        }
        log.info("COSTA: Finalizing bill {} (allowUnpriced={})", billId, allowUnpriced);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return extractData(response);
    }

    /**
     * List dead-lettered money events from COSTA's ops surface.
     *
     * @param status optional filter (DEAD, REPLAYED); null/blank = all
     * @return array of failed money events (newest first)
     */
    public JsonNode listFailedMoneyEvents(String status) {
        String url = baseUrl + "/internal/v1/finance/failed-money-events";
        if (status != null && !status.isBlank()) {
            url = UriComponentsBuilder.fromHttpUrl(url).queryParam("status", status).toUriString();
        }
        log.info("COSTA: Listing failed money events (status={})", status);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Replay a dead-lettered money event by re-publishing its original payload
     * onto the original topic. Safe: money consumers are idempotent.
     */
    public JsonNode replayFailedMoneyEvent(long id) {
        String url = baseUrl + "/internal/v1/finance/failed-money-events/" + id + "/replay";
        log.info("COSTA: Replaying failed money event {}", id);
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
     * List bills referencing an encounter (read-only clinical billing visibility).
     */
    public JsonNode getBillsByEncounter(String encounterId) {
        String enc = UriUtils.encodePathSegment(encounterId, StandardCharsets.UTF_8);
        String url = baseUrl + "/costa/v1/bills/by-encounter/" + enc;
        log.info("COSTA: Listing bills for encounter={}", encounterId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
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
     * Tariff / charge preview for registration and counselling flows ({@code POST /costa/v1/estimate}).
     */
    public JsonNode createEstimate(Map<String, Object> body) {
        String url = baseUrl + "/costa/v1/estimate";
        log.info("COSTA: Creating estimate");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
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

    // ── Patient financials (COSTA) ────────────────────────────────────

    public JsonNode getFinancePatientAccount(String cpid) {
        String enc = UriUtils.encodePathSegment(cpid, StandardCharsets.UTF_8);
        String url = baseUrl + "/costa/v1/finance/patient-accounts/" + enc;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFinancePatientTransactions(String cpid, int page, int size,
                                                   String dateFrom, String dateTo) {
        String enc = UriUtils.encodePathSegment(cpid, StandardCharsets.UTF_8);
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/costa/v1/finance/patient-accounts/" + enc + "/transactions")
                .queryParam("page", page)
                .queryParam("size", size);
        if (dateFrom != null && !dateFrom.isBlank()) {
            b.queryParam("dateFrom", dateFrom);
        }
        if (dateTo != null && !dateTo.isBlank()) {
            b.queryParam("dateTo", dateTo);
        }
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(b.toUriString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFinancePatientOutstanding(String cpid) {
        String enc = UriUtils.encodePathSegment(cpid, StandardCharsets.UTF_8);
        String url = baseUrl + "/costa/v1/finance/patient-accounts/" + enc + "/outstanding";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode postFinancePatientPayment(String cpid, Map<String, Object> body) {
        String enc = UriUtils.encodePathSegment(cpid, StandardCharsets.UTF_8);
        String url = baseUrl + "/costa/v1/finance/patient-accounts/" + enc + "/payments";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode postFinancePaymentPlan(Map<String, Object> body) {
        String url = baseUrl + "/costa/v1/finance/payment-plans";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFinancePaymentPlans(String patientCpid) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/costa/v1/finance/payment-plans")
                .queryParam("patient_cpid", patientCpid)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode putFinancePaymentPlanInstallment(String planId, Map<String, Object> body) {
        String url = baseUrl + "/costa/v1/finance/payment-plans/" + planId + "/pay";
        Object payload = body == null ? Map.of() : body;
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(payload), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFinancePaymentPlan(String planId) {
        String url = baseUrl + "/costa/v1/finance/payment-plans/" + planId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode postFinanceDocumentGenerate(Map<String, Object> body) {
        String url = baseUrl + "/costa/v1/finance/documents/generate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFinanceDocuments(String subjectRef, String subjectType, String docType, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/costa/v1/finance/documents")
                .queryParam("subject_ref", subjectRef)
                .queryParam("page", page)
                .queryParam("size", size);
        if (subjectType != null && !subjectType.isBlank()) {
            b.queryParam("subject_type", subjectType);
        }
        if (docType != null && !docType.isBlank()) {
            b.queryParam("doc_type", docType);
        }
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(b.toUriString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFinanceDocument(String docId) {
        String url = baseUrl + "/costa/v1/finance/documents/" + docId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Service access decisions (COSTA /costa/v1/service-access-decisions) ─

    public JsonNode postServiceAccessDecision(Map<String, Object> body) {
        String url = baseUrl + "/costa/v1/service-access-decisions";
        log.info("COSTA: Register service access decision");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getServiceAccessDecisions(String encounterId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/costa/v1/service-access-decisions");
        if (encounterId != null && !encounterId.isBlank()) {
            b.queryParam("encounter_id", encounterId);
        }
        log.info("COSTA: List service access decisions encounterId={}", encounterId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(b.toUriString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getServiceAccessDecision(String id) {
        String enc = UriUtils.encodePathSegment(id, StandardCharsets.UTF_8);
        String url = baseUrl + "/costa/v1/service-access-decisions/" + enc;
        log.info("COSTA: Get service access decision id={}", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Institutional financial reporting (COSTA internal) ────────────

    public ResponseEntity<String> costaInternalGet(String pathAndQuery) {
        String url = baseUrl + (pathAndQuery.startsWith("/") ? pathAndQuery : "/" + pathAndQuery);
        log.info("COSTA internal GET {}", url);
        return restTemplate.exchange(url, HttpMethod.GET, null, String.class);
    }

    public ResponseEntity<String> costaInternalPost(String path, Object body) {
        String url = baseUrl + (path.startsWith("/") ? path : "/" + path);
        log.info("COSTA internal POST {}", url);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body), String.class);
    }

    public ResponseEntity<String> costaInternalPut(String path, Object body) {
        String url = baseUrl + (path.startsWith("/") ? path : "/" + path);
        log.info("COSTA internal PUT {}", url);
        return restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), String.class);
    }

    /**
     * Proxies COSTA {@code /api/costa/...} tariff intel, upload, cost events, and billing decisions.
     *
     * @param relativePath path starting with {@code /api/costa} or suffix after it (e.g. {@code /tariff-lists})
     */
    public ResponseEntity<String> costaIntelExchange(HttpMethod method, String relativePath, String body) {
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        if (!path.startsWith("/api/costa")) {
            path = "/api/costa" + path;
        }
        String url = baseUrl + path;
        log.info("COSTA intel {} {}", method, url);
        HttpHeaders headers = new HttpHeaders();
        if (body != null && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(null, headers), String.class);
    }

    // ── Financial lifecycle (COSTA /costa/v1/finance/lifecycle) ───────

    public ResponseEntity<String> financeLifecycleGet(String relativePath) {
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        String url = baseUrl + "/costa/v1/finance/lifecycle" + path;
        log.info("COSTA finance lifecycle GET {}", url);
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> financeLifecyclePost(String relativePath, String jsonBody) {
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        String url = baseUrl + "/costa/v1/finance/lifecycle" + path;
        log.info("COSTA finance lifecycle POST {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = jsonBody != null ? jsonBody : "{}";
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
