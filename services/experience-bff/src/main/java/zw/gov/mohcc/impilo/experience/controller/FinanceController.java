package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;

import java.util.Map;

/**
 * Finance BFF Controller — bridges Experience UI finance pages to the
 * COSTA costing-engine sovereign service.
 *
 * <p>Transforms COSTA's domain model (bills, tariffs, payments) into
 * the JSON:API-style resource format the UI expects.</p>
 *
 * <p>Supported endpoints:</p>
 * <ul>
 *   <li>GET /internal/v1/finance/billing — list bills as billing resources</li>
 *   <li>GET /internal/v1/finance/billing/{id} — single bill detail</li>
 *   <li>GET /internal/v1/finance/tariffs — list tariffs</li>
 *   <li>GET /internal/v1/finance/payments — list payments</li>
 *   <li>GET /internal/v1/finance/claims — list insurance claim packs</li>
 *   <li>GET /internal/v1/finance/claims/{id} — claim detail with line items and adjudication history</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/finance")
public class FinanceController {

    private static final Logger log = LoggerFactory.getLogger(FinanceController.class);

    private final CostaServiceClient costaClient;
    private final ObjectMapper objectMapper;

    public FinanceController(CostaServiceClient costaClient, ObjectMapper objectMapper) {
        this.costaClient = costaClient;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /internal/v1/finance/billing
     *
     * Lists bills from COSTA and transforms them into the billing resource
     * format expected by the UI (id, type: "invoice", attributes with
     * invoiceNumber, patient, amount, currency, status, date).
     */
    @GetMapping("/billing")
    public ResponseEntity<Map<String, Object>> listBilling(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status) {
        try {
            JsonNode costaData = costaClient.listBills(page, size, status);
            ArrayNode resources = objectMapper.createArrayNode();

            JsonNode items = costaData != null && costaData.has("items") ? costaData.get("items") : null;
            if (items != null && items.isArray()) {
                for (JsonNode bill : items) {
                    resources.add(toBillingResource(bill));
                }
            }

            return ResponseEntity.ok(buildPagedResponse(resources, costaData));
        } catch (Exception e) {
            log.error("Failed to fetch billing from COSTA: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    /**
     * GET /internal/v1/finance/billing/{id}
     *
     * Fetches a single bill with its lines and parties from COSTA.
     */
    @GetMapping("/billing/{id}")
    public ResponseEntity<Map<String, Object>> getBillingDetail(@PathVariable String id) {
        try {
            JsonNode costaData = costaClient.getBill(id);
            JsonNode bill = costaData != null && costaData.has("bill") ? costaData.get("bill") : costaData;
            ObjectNode resource = toBillingResource(bill);

            // Attach lines and parties as nested attributes
            if (costaData != null && costaData.has("lines")) {
                resource.withObject("/attributes").set("lineItems", costaData.get("lines"));
            }
            if (costaData != null && costaData.has("parties")) {
                resource.withObject("/attributes").set("parties", costaData.get("parties"));
            }

            return ResponseEntity.ok(Map.of("data", resource));
        } catch (Exception e) {
            log.error("Failed to fetch bill detail from COSTA: {}", e.getMessage());
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND", "message", "Bill not found")));
        }
    }

    // ── Bill Lifecycle Write Operations ─────────────────────────────

    /**
     * POST /internal/v1/finance/billing/{id}/submit
     *
     * Submit a bill for approval (DRAFT/ACCUMULATING → APPROVAL_PENDING).
     */
    @PostMapping("/billing/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitBillForApproval(@PathVariable String id) {
        try {
            JsonNode result = costaClient.submitForApproval(id);
            return ResponseEntity.ok(Map.of("data", toBillingResource(result)));
        } catch (Exception e) {
            log.error("Failed to submit bill {} for approval: {}", id, e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "SUBMIT_FAILED", "message", e.getMessage())));
        }
    }

    /**
     * POST /internal/v1/finance/billing/{id}/approve
     *
     * Approve a bill (APPROVAL_PENDING → APPROVED).
     */
    @PostMapping("/billing/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveBill(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String note = body != null ? body.get("note") : null;
            JsonNode result = costaClient.approveBill(id, note);
            return ResponseEntity.ok(Map.of("data", toBillingResource(result)));
        } catch (Exception e) {
            log.error("Failed to approve bill {}: {}", id, e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "APPROVE_FAILED", "message", e.getMessage())));
        }
    }

    /**
     * POST /internal/v1/finance/billing/{id}/finalize
     *
     * Finalize an approved bill (APPROVED → FINAL).
     */
    @PostMapping("/billing/{id}/finalize")
    public ResponseEntity<Map<String, Object>> finalizeBill(@PathVariable String id) {
        try {
            JsonNode result = costaClient.finalizeBill(id);
            return ResponseEntity.ok(Map.of("data", toBillingResource(result)));
        } catch (Exception e) {
            log.error("Failed to finalize bill {}: {}", id, e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "FINALIZE_FAILED", "message", e.getMessage())));
        }
    }

    /**
     * POST /internal/v1/finance/billing/{id}/invoice
     *
     * Issue an invoice for a finalized bill.
     */
    @PostMapping("/billing/{id}/invoice")
    public ResponseEntity<Map<String, Object>> issueInvoice(@PathVariable String id) {
        try {
            JsonNode result = costaClient.issueInvoice(id);
            ObjectNode resource = objectMapper.createObjectNode();
            resource.put("id", textOrEmpty(result, "invoiceId"));
            resource.put("type", "invoice-document");
            ObjectNode attrs = resource.putObject("attributes");
            attrs.put("invoiceNumber", textOrEmpty(result, "invoiceNumber"));
            attrs.put("billId", textOrEmpty(result, "billId"));
            attrs.put("issuedAt", textOrEmpty(result, "issuedAt"));
            return ResponseEntity.status(201).body(Map.of("data", resource));
        } catch (Exception e) {
            log.error("Failed to issue invoice for bill {}: {}", id, e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "INVOICE_FAILED", "message", e.getMessage())));
        }
    }

    /**
     * POST /internal/v1/finance/billing/{id}/payment
     *
     * Create a payment intent for a bill via COSTA.
     * COSTA creates the local PaymentEntity; MusheX auto-creates the
     * PaymentIntentEntity when the bill.finalized event arrives.
     */
    @PostMapping("/billing/{id}/payment")
    public ResponseEntity<Map<String, Object>> createPaymentIntent(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        try {
            String paymentType = body.getOrDefault("paymentType", "FULL");
            String amount = body.get("amount");
            if (amount == null || amount.isBlank()) {
                return ResponseEntity.status(400).body(Map.of(
                        "error", Map.of("code", "MISSING_AMOUNT", "message", "amount is required")));
            }
            JsonNode result = costaClient.createPaymentIntent(id, paymentType, amount);
            ObjectNode resource = toPaymentResource(result);
            return ResponseEntity.status(201).body(Map.of("data", resource));
        } catch (Exception e) {
            log.error("Failed to create payment intent for bill {}: {}", id, e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "PAYMENT_FAILED", "message", e.getMessage())));
        }
    }

    /**
     * GET /internal/v1/finance/billing/{id}/payments
     *
     * List payments for a specific bill from COSTA.
     */
    @GetMapping("/billing/{id}/payments")
    public ResponseEntity<Map<String, Object>> getBillPayments(@PathVariable String id) {
        try {
            JsonNode costaData = costaClient.getBillPayments(id);
            ArrayNode resources = objectMapper.createArrayNode();
            if (costaData != null && costaData.isArray()) {
                for (JsonNode payment : costaData) {
                    resources.add(toPaymentResource(payment));
                }
            }
            return ResponseEntity.ok(Map.of("data", resources));
        } catch (Exception e) {
            log.error("Failed to fetch payments for bill {}: {}", id, e.getMessage());
            return ResponseEntity.ok(Map.of("data", objectMapper.createArrayNode()));
        }
    }

    /**
     * POST /internal/v1/finance/billing/{id}/payments/{paymentId}/cancel
     *
     * Cancel a PENDING payment for a bill.
     */
    @PostMapping("/billing/{id}/payments/{paymentId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelPayment(
            @PathVariable String id, @PathVariable String paymentId) {
        try {
            JsonNode result = costaClient.cancelPayment(id, paymentId);
            return ResponseEntity.ok(Map.of("data", toPaymentResource(result)));
        } catch (Exception e) {
            log.error("Failed to cancel payment {} for bill {}: {}", paymentId, id, e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "CANCEL_FAILED", "message", e.getMessage())));
        }
    }

    // ── Tariffs & Payments (global lists) ────────────────────────────

    /**
     * GET /internal/v1/finance/tariffs
     *
     * Lists tariffs from COSTA, mapping to the UI's tariff resource format.
     */
    @GetMapping("/tariffs")
    public ResponseEntity<Map<String, Object>> listTariffs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            JsonNode costaData = costaClient.listTariffs(page, size);
            ArrayNode resources = objectMapper.createArrayNode();

            JsonNode items = costaData != null && costaData.has("items") ? costaData.get("items") : null;
            if (items != null && items.isArray()) {
                for (JsonNode tariff : items) {
                    resources.add(toTariffResource(tariff));
                }
            }

            return ResponseEntity.ok(buildPagedResponse(resources, costaData));
        } catch (Exception e) {
            log.error("Failed to fetch tariffs from COSTA: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    /**
     * GET /internal/v1/finance/payments
     *
     * Lists payments from COSTA, mapping to the UI's payment resource format.
     */
    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            JsonNode costaData = costaClient.listPayments(page, size);
            ArrayNode resources = objectMapper.createArrayNode();

            JsonNode items = costaData != null && costaData.has("items") ? costaData.get("items") : null;
            if (items != null && items.isArray()) {
                for (JsonNode payment : items) {
                    resources.add(toPaymentResource(payment));
                }
            }

            return ResponseEntity.ok(buildPagedResponse(resources, costaData));
        } catch (Exception e) {
            log.error("Failed to fetch payments from COSTA: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    /**
     * GET /internal/v1/finance/claims
     *
     * Lists claim packs from COSTA, mapping to the UI's claim resource format.
     */
    @GetMapping("/claims")
    public ResponseEntity<Map<String, Object>> listClaims(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            JsonNode costaData = costaClient.listClaims(page, size);
            ArrayNode resources = objectMapper.createArrayNode();

            JsonNode items = costaData != null && costaData.has("items") ? costaData.get("items") : null;
            if (items != null && items.isArray()) {
                for (JsonNode claim : items) {
                    resources.add(toClaimResource(claim));
                }
            }

            return ResponseEntity.ok(buildPagedResponse(resources, costaData));
        } catch (Exception e) {
            log.error("Failed to fetch claims from COSTA: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    /**
     * GET /internal/v1/finance/claims/{id}
     *
     * Fetches a single claim pack from COSTA with bill context.
     */
    @GetMapping("/claims/{id}")
    public ResponseEntity<Map<String, Object>> getClaimDetail(@PathVariable String id) {
        try {
            JsonNode costaData = costaClient.getClaim(id);
            ObjectNode resource = toClaimResource(costaData);

            // Attempt to enrich with bill lines if payload contains billId
            String billId = textOrEmpty(costaData, "billId");
            if (!billId.isEmpty()) {
                try {
                    JsonNode billData = costaClient.getBill(billId);
                    if (billData != null && billData.has("lines")) {
                        ArrayNode lineItems = objectMapper.createArrayNode();
                        for (JsonNode line : billData.get("lines")) {
                            ObjectNode li = objectMapper.createObjectNode();
                            li.put("code", textOrEmpty(line, "msikaCode"));
                            li.put("description", textOrEmpty(line, "description"));
                            li.put("quantity", line.has("qty") ? line.get("qty").asDouble() : 0);
                            li.put("unitPrice", line.has("unitPrice") ? line.get("unitPrice").asDouble() : 0);
                            li.put("total", line.has("amount") ? line.get("amount").asDouble() : 0);
                            lineItems.add(li);
                        }
                        resource.withObject("/attributes").set("lineItems", lineItems);
                    }
                } catch (Exception billErr) {
                    log.warn("Failed to enrich claim with bill lines: {}", billErr.getMessage());
                }

                // Enrich with adjudication history from bill audit trail
                try {
                    JsonNode auditData = costaClient.getBillAudit(billId);
                    ArrayNode adjudicationHistory = objectMapper.createArrayNode();

                    // Map approvals from audit data
                    if (auditData != null && auditData.has("approvals")) {
                        for (JsonNode approval : auditData.get("approvals")) {
                            ObjectNode event = objectMapper.createObjectNode();
                            event.put("date", textOrEmpty(approval, "createdAt"));
                            event.put("status", mapApprovalStatusToAdjudication(
                                    textOrDefault(approval, "status", "PENDING")));
                            event.put("notes", textOrDefault(approval, "note", ""));
                            event.put("adjudicator", textOrDefault(approval, "approverActorId", "System"));
                            adjudicationHistory.add(event);
                        }
                    }

                    // Add the claim's own status as the latest event
                    ObjectNode claimEvent = objectMapper.createObjectNode();
                    claimEvent.put("date", textOrEmpty(costaData, "createdAt"));
                    claimEvent.put("status", "SUBMITTED");
                    claimEvent.put("notes", "Claim pack created");
                    claimEvent.put("adjudicator", "System");
                    adjudicationHistory.add(claimEvent);

                    if (costaData.has("sentAt") && !costaData.get("sentAt").isNull()) {
                        ObjectNode sentEvent = objectMapper.createObjectNode();
                        sentEvent.put("date", costaData.get("sentAt").asText());
                        sentEvent.put("status", "SUBMITTED");
                        sentEvent.put("notes", "Claim sent to insurer");
                        sentEvent.put("adjudicator", "System");
                        adjudicationHistory.add(sentEvent);
                    }

                    resource.withObject("/attributes").set("adjudicationHistory", adjudicationHistory);
                } catch (Exception auditErr) {
                    log.warn("Failed to enrich claim with adjudication history: {}", auditErr.getMessage());
                    resource.withObject("/attributes").set("adjudicationHistory",
                            objectMapper.createArrayNode());
                }
            }

            return ResponseEntity.ok(Map.of("data", resource));
        } catch (Exception e) {
            log.error("Failed to fetch claim detail from COSTA: {}", e.getMessage());
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND", "message", "Claim not found")));
        }
    }

    // ── Resource Mappers ─────────────────────────────────────────────

    /**
     * Maps a COSTA BillHeaderEntity JSON to the UI's billing resource format.
     * UI expects: { id, type: "invoice", attributes: { invoiceNumber, patient, amount, currency, status, date } }
     */
    private ObjectNode toBillingResource(JsonNode bill) {
        ObjectNode resource = objectMapper.createObjectNode();
        resource.put("id", textOrEmpty(bill, "billId"));
        resource.put("type", "invoice");

        ObjectNode attrs = resource.putObject("attributes");
        attrs.put("invoiceNumber", textOrEmpty(bill, "billId"));
        attrs.put("patient", textOrEmpty(bill, "encounterId"));
        attrs.put("amount", bill != null && bill.has("totalPayable")
                ? bill.get("totalPayable").asDouble() : 0.0);
        attrs.put("currency", textOrDefault(bill, "currency", "USD"));
        attrs.put("status", mapBillStatusToUi(textOrEmpty(bill, "status")));
        attrs.put("date", textOrEmpty(bill, "createdAt"));
        attrs.put("billType", textOrEmpty(bill, "billType"));
        attrs.put("facilityId", textOrEmpty(bill, "facilityId"));
        attrs.put("totalCost", bill != null && bill.has("totalCost")
                ? bill.get("totalCost").asDouble() : 0.0);
        attrs.put("totalCharge", bill != null && bill.has("totalCharge")
                ? bill.get("totalCharge").asDouble() : 0.0);

        return resource;
    }

    /**
     * Maps a COSTA TariffEntity JSON to the UI's tariff resource format.
     * UI expects: { id, type: "tariff", attributes: { serviceCode, description, tariffAmount, currency, effectiveDate, status } }
     */
    private ObjectNode toTariffResource(JsonNode tariff) {
        ObjectNode resource = objectMapper.createObjectNode();
        resource.put("id", tariff.has("id") ? tariff.get("id").asText() : "");
        resource.put("type", "tariff");

        ObjectNode attrs = resource.putObject("attributes");
        attrs.put("serviceCode", textOrEmpty(tariff, "tariffCode"));
        attrs.put("description", textOrEmpty(tariff, "description"));
        attrs.put("tariffAmount", tariff != null && tariff.has("price")
                ? tariff.get("price").asDouble() : 0.0);
        attrs.put("currency", textOrDefault(tariff, "currency", "USD"));
        attrs.put("effectiveDate", textOrEmpty(tariff, "effectiveFrom"));
        attrs.put("status", textOrDefault(tariff, "status", "ACTIVE"));
        attrs.put("msikaCode", textOrEmpty(tariff, "msikaCode"));
        attrs.put("facilityCategory", textOrEmpty(tariff, "facilityCategory"));
        attrs.put("patientCategory", textOrEmpty(tariff, "patientCategory"));

        return resource;
    }

    /**
     * Maps a COSTA PaymentEntity JSON to the UI's payment resource format.
     * UI expects: { id, type: "payment", attributes: { paymentNumber, payer, amount, currency, method, status, date } }
     */
    private ObjectNode toPaymentResource(JsonNode payment) {
        ObjectNode resource = objectMapper.createObjectNode();
        resource.put("id", payment.has("id") ? payment.get("id").asText() : "");
        resource.put("type", "payment");

        ObjectNode attrs = resource.putObject("attributes");
        attrs.put("paymentNumber", payment.has("id") ? "PAY-" + payment.get("id").asText() : "");
        attrs.put("payer", textOrEmpty(payment, "billId"));
        attrs.put("amount", payment != null && payment.has("amount")
                ? payment.get("amount").asDouble() : 0.0);
        attrs.put("currency", textOrDefault(payment, "currency", "USD"));
        attrs.put("method", textOrDefault(payment, "paymentType", "FULL"));
        attrs.put("status", textOrDefault(payment, "status", "PENDING"));
        attrs.put("date", textOrEmpty(payment, "createdAt"));
        attrs.put("paidAmount", payment != null && payment.has("paidAmount")
                ? payment.get("paidAmount").asDouble() : 0.0);
        attrs.put("billId", textOrEmpty(payment, "billId"));

        return resource;
    }

    /**
     * Maps a COSTA ClaimPackEntity JSON to the UI's claim resource format.
     * UI expects: { id, type: "claim", attributes: { claimNumber, patient, scheme, amount, currency, status, date } }
     */
    private ObjectNode toClaimResource(JsonNode claim) {
        ObjectNode resource = objectMapper.createObjectNode();
        resource.put("id", claim.has("id") ? claim.get("id").asText() : "");
        resource.put("type", "claim");

        ObjectNode attrs = resource.putObject("attributes");
        attrs.put("claimNumber", "CLM-" + (claim.has("id") ? claim.get("id").asText() : ""));
        attrs.put("scheme", textOrDefault(claim, "insurerRef", "Government"));
        attrs.put("status", mapClaimStatusToUi(textOrDefault(claim, "status", "PENDING")));
        attrs.put("date", textOrEmpty(claim, "createdAt"));
        attrs.put("claimType", textOrDefault(claim, "claimType", "INITIAL"));
        attrs.put("billId", textOrEmpty(claim, "billId"));

        // Extract amount and patient from the claim payload JSONB
        JsonNode payloadNode = null;
        if (claim.has("payload")) {
            try {
                String payloadStr = claim.get("payload").asText("");
                if (!payloadStr.isEmpty() && !payloadStr.equals("{}")) {
                    payloadNode = objectMapper.readTree(payloadStr);
                }
            } catch (Exception e) {
                // payload is already a JsonNode (not a string)
                payloadNode = claim.get("payload").isObject() ? claim.get("payload") : null;
            }
        }

        if (payloadNode != null) {
            attrs.put("amount", payloadNode.has("insurerPayable")
                    ? payloadNode.get("insurerPayable").asDouble() : 0.0);
            attrs.put("currency", "USD");
            attrs.put("patient", textOrEmpty(payloadNode, "facilityId"));
        } else {
            attrs.put("amount", 0.0);
            attrs.put("currency", "USD");
            attrs.put("patient", "");
        }

        return resource;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Maps COSTA BillStatus values to UI-visible status. Passes through
     * the real status so the UI can show lifecycle-aware action buttons.
     */
    private String mapBillStatusToUi(String costaStatus) {
        if (costaStatus == null) return "DRAFT";
        return costaStatus;
    }

    /**
     * Maps COSTA ClaimPackStatus values to the status set the claims UI understands.
     */
    private String mapClaimStatusToUi(String costaStatus) {
        if (costaStatus == null) return "SUBMITTED";
        return switch (costaStatus) {
            case "PENDING", "SENT" -> "SUBMITTED";
            case "ACKNOWLEDGED" -> "ADJUDICATED";
            case "REJECTED", "FAILED" -> "REJECTED";
            default -> costaStatus;
        };
    }

    /**
     * Maps COSTA ApprovalStatus to the adjudication status the claims UI understands.
     */
    private String mapApprovalStatusToAdjudication(String approvalStatus) {
        if (approvalStatus == null) return "SUBMITTED";
        return switch (approvalStatus) {
            case "PENDING" -> "SUBMITTED";
            case "APPROVED" -> "ADJUDICATED";
            case "REJECTED" -> "REJECTED";
            default -> approvalStatus;
        };
    }

    private Map<String, Object> buildPagedResponse(ArrayNode resources, JsonNode costaData) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        if (costaData != null) {
            if (costaData.has("page")) meta.put("page", costaData.get("page").asInt());
            if (costaData.has("size")) meta.put("size", costaData.get("size").asInt());
            if (costaData.has("totalElements")) meta.put("total_elements", costaData.get("totalElements").asLong());
            if (costaData.has("totalPages")) meta.put("total_pages", costaData.get("totalPages").asInt());
        }
        return Map.of("data", resources, "meta", meta);
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return "";
        return node.get(field).asText("");
    }

    private static String textOrDefault(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return defaultValue;
        String val = node.get(field).asText("");
        return val.isEmpty() ? defaultValue : val;
    }
}
