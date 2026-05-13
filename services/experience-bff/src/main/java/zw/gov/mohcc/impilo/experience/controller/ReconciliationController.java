package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/internal/v1/finance/reconciliation")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final MushexServiceClient mushexClient;
    private final CostaServiceClient costaClient;
    private final ObjectMapper objectMapper;

    public ReconciliationController(MushexServiceClient mushexClient, CostaServiceClient costaClient) {
        this.mushexClient = mushexClient;
        this.costaClient = costaClient;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping(value = "/import-statement", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> importStatement(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("import statement", requestId, correlationId, () -> mushexClient.importStatement(body));
    }

    @GetMapping("/unmatched")
    public ResponseEntity<String> getUnmatched(
            @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("list unmatched", requestId, correlationId,
                () -> mushexClient.getUnmatched(new LinkedMultiValueMap<>(queryParams)));
    }

    @PostMapping(value = "/match", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> matchEntry(
            @RequestParam String reconId,
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        return forward("match entry", requestId, correlationId, () -> mushexClient.matchEntry(reconId, body));
    }

    @GetMapping("/triple-match")
    public ResponseEntity<String> tripleMatch(
            @RequestParam String encounterId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        if (encounterId == null || encounterId.isBlank()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body("{\"data\":[],\"error\":{\"code\":\"ENCOUNTER_REQUIRED\",\"message\":\"encounterId is required\"}}");
        }
        try {
            ResponseEntity<String> invoiceResp =
                    costaClient.financeLifecycleGet("/invoices?encounter_id=" + encode(encounterId));
            List<Map<String, Object>> invoiceRows = extractRows(invoiceResp.getBody());

            Set<String> billIds = new LinkedHashSet<>();
            for (Map<String, Object> row : invoiceRows) {
                String billId = readString(row.get("bill_id"));
                if (billId != null && !billId.isBlank()) billIds.add(billId);
            }

            List<Map<String, Object>> intentRows = List.of();
            if (!billIds.isEmpty()) {
                ResponseEntity<String> intentResp = mushexClient.listPaymentIntentsBySource("COSTA_BILL", null, new ArrayList<>(billIds));
                intentRows = extractRows(intentResp.getBody());
            }

            Map<String, Map<String, Object>> intentByBillId = new LinkedHashMap<>();
            Set<String> intentIds = new LinkedHashSet<>();
            for (Map<String, Object> intent : intentRows) {
                String sourceId = readString(intent.get("sourceId"));
                String intentId = readString(intent.get("intentId"));
                if (sourceId != null && intentId != null) {
                    intentByBillId.putIfAbsent(sourceId, intent);
                    intentIds.add(intentId);
                }
            }

            List<Map<String, Object>> settlementRows = List.of();
            if (!intentIds.isEmpty()) {
                ResponseEntity<String> settlementResp = mushexClient.listSettlements(null, new ArrayList<>(intentIds));
                settlementRows = extractRows(settlementResp.getBody());
            }

            List<String> settlementIds = new ArrayList<>();
            for (Map<String, Object> s : settlementRows) {
                String id = readString(s.get("settlementId"));
                if (id != null && !id.isBlank()) settlementIds.add(id);
            }

            List<Map<String, Object>> joined = new ArrayList<>();
            for (Map<String, Object> invoiceRow : invoiceRows) {
                String billId = readString(invoiceRow.get("bill_id"));
                Map<String, Object> intent = billId == null ? null : intentByBillId.get(billId);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("encounter_id", encounterId);
                row.put("bill_id", billId);
                row.put("invoice_id", readString(invoiceRow.get("invoice_id")));
                row.put("mushex_intent_id", intent != null ? readString(intent.get("intentId")) : null);
                row.put("intent_status", intent != null ? readString(intent.get("status")) : null);
                row.put("settlement_count", settlementRows.size());
                row.put("settlement_ids", settlementIds);
                row.put("invoice_row", invoiceRow);
                row.put("intent_row", intent);
                joined.add(row);
            }

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("data", joined);
            envelope.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId,
                    "encounter_id", encounterId,
                    "invoice_count", invoiceRows.size(),
                    "intent_count", intentRows.size(),
                    "settlement_count", settlementRows.size()));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body(objectMapper.writeValueAsString(envelope));
        } catch (Exception exception) {
            log.warn("Reconciliation triple-match failed: {}", exception.getMessage());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body("{\"data\":[],\"meta\":{\"encounter_id\":\"" + encounterId + "\"}}");
        }
    }

    private ResponseEntity<String> forward(String action, String requestId, String correlationId, UpstreamCall call) {
        try {
            return withHeaders(call.run(), requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Reconciliation {} failed status={}", action, exception.getStatusCode());
            return withHeaders(exception, requestId, correlationId);
        }
    }

    private ResponseEntity<String> withHeaders(ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    private ResponseEntity<String> withHeaders(HttpStatusCodeException upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getResponseHeaders() != null ? upstream.getResponseHeaders().getContentType() : null;
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getResponseBodyAsString());
    }

    @FunctionalInterface
    private interface UpstreamCall {
        ResponseEntity<String> run();
    }

    private List<Map<String, Object>> extractRows(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = null;
            if (root.isArray()) {
                arr = root;
            } else if (root.has("data") && root.get("data").isArray()) {
                arr = root.get("data");
            } else if (root.has("items") && root.get("items").isArray()) {
                arr = root.get("items");
            } else if (root.has("content") && root.get("content").isArray()) {
                arr = root.get("content");
            }
            if (arr == null) return List.of();
            return objectMapper.convertValue(arr, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String readString(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        return String.valueOf(value);
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
