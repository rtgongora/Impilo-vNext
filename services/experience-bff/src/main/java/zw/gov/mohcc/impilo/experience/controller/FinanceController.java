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

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Maps COSTA BillStatus values to the simpler status set the billing UI understands.
     */
    private String mapBillStatusToUi(String costaStatus) {
        if (costaStatus == null) return "DRAFT";
        return switch (costaStatus) {
            case "DRAFT", "ACCUMULATING" -> "DRAFT";
            case "APPROVAL_PENDING" -> "ISSUED";
            case "APPROVED", "FINAL" -> "ISSUED";
            case "VOID", "ADJUSTED" -> "OVERDUE";
            default -> costaStatus;
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
