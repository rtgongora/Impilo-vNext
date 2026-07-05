package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.service.CoreTransactionCompositionService;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.UUID;

/**
 * Encounter management — proxies canonical PCT encounter workflow.
 */
@RestController
@RequestMapping("/internal/v1/encounters")
public class EncounterController {

    private static final Logger log = LoggerFactory.getLogger(EncounterController.class);

    private final PctServiceClient pctClient;
    private final ClinicalKnowledgePlatformClient clinicalClient;
    private final CostaServiceClient costaClient;

    public EncounterController(PctServiceClient pctClient, ClinicalKnowledgePlatformClient clinicalClient,
                               CostaServiceClient costaClient) {
        this.pctClient = pctClient;
        this.clinicalClient = clinicalClient;
        this.costaClient = costaClient;
    }

    public record CreateEncounterRequest(
            @JsonProperty("patient_id") String patientId,
            @JsonProperty("facility_id") String facilityId,
            @JsonProperty("encounter_type") String encounterType,
            @JsonProperty("encounter_context") String encounterContext,
            @JsonProperty("entry_point") String entryPoint,
            @JsonProperty("modality") String modality,
            @JsonProperty("virtual_mode") String virtualMode,
            @JsonProperty("care_setting") String careSetting,
            @JsonProperty("priority") String priority,
            @JsonProperty("triage_category") String triageCategory,
            @JsonProperty("pathway_ref") String pathwayRef,
            @JsonProperty("protocol_ref") String protocolRef,
            @JsonProperty("chief_complaint") String chiefComplaint,
            @JsonProperty("journey_id") String journeyId,
            @JsonProperty("cpid") String cpid) {}

    public record UpdateEncounterPathwayProtocolRequest(
            @JsonProperty("pathway_ref") String pathwayRef,
            @JsonProperty("protocol_ref") String protocolRef) {}

    /**
     * Resolve the Cadre Engine decision (C9) that drives the adaptive Encounter Cockpit spine. PCT owns and
     * audits the decision; the BFF composes only. The cockpit renders strictly from {@code cockpitSpine} —
     * disabled actions are not rendered as live buttons.
     */
    @PostMapping("/cadre-decision")
    public ResponseEntity<Map<String, Object>> cadreDecision(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = pctClient.resolveCadreDecision(body == null ? Map.of() : body);
            if (data == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No cadre decision returned", requestId, correlationId);
            }
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT cadre-decision failed: {}", e.getMessage());
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listEncounters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MISSING_PATIENT_ID", "message", "patient_id query parameter is required"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }

        try {
            JsonNode pctData = pctClient.getPatientTimeline(patientId);
            if (pctData == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No encounter list payload returned", requestId, correlationId);
            }
            return ResponseEntity.ok(Map.of(
                    "data", pctData,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT unavailable for encounter list: {}", e.getMessage());
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEncounter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        try {
            long encId = Long.parseLong(id.trim());
            JsonNode data = pctClient.getEncounter(encId);
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PCT_UNAVAILABLE", "No encounter payload returned", requestId, correlationId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "INVALID_ENCOUNTER_ID", "message", "Encounter id must be the numeric PCT encounter id"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getEncounter failed: {}", e.getMessage());
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createEncounter(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody CreateEncounterRequest body) {

        String patientId = body.patientId();
        String encounterType = body.encounterType();
        String journeyId = body.journeyId();

        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "patient_id is required")));
        }

        if (journeyId == null || journeyId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MISSING_JOURNEY_ID", "message", "journey_id is required for canonical encounter start"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }

        try {
            JsonNode pctEnc = pctClient.startEncounter(
                    journeyId,
                    encounterType,
                    body.encounterContext(),
                    body.entryPoint(),
                    body.modality(),
                    body.virtualMode(),
                    body.careSetting(),
                    body.priority(),
                    body.triageCategory(),
                    body.pathwayRef(),
                    body.protocolRef());
            if (pctEnc == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No encounter create payload returned", requestId, correlationId);
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            String encounterId = extractEncounterId(pctEnc);
            if (encounterId != null) {
                meta.put("encounter_id", encounterId);
                meta.put("core_transaction_id", CoreTransactionCompositionService.encounterTransactionId(encounterId));
            }
            if (journeyId != null && !journeyId.isBlank()) {
                meta.put("journey_id", journeyId);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", pctEnc,
                    "meta", meta));
        } catch (Exception e) {
            log.warn("PCT startEncounter failed: {}", e.getMessage());
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Map<String, Object>> closeEncounter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        try {
            long encId = Long.parseLong(id.trim());
            JsonNode pct = pctClient.completeEncounter(encId);
            if (pct == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No encounter close payload returned", requestId, correlationId);
            }
            Map<String, Object> meta = new LinkedHashMap<>(Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId));
            // Encounter→bill trigger for the standard web close path (parity with the
            // mobile close path). Draft-only and non-blocking: COSTA owns billing truth
            // and dedupes per encounter (existing DRAFT/ACCUMULATING bill is returned,
            // never duplicated), so repeated close requests cannot double-bill.
            String costaBillId = createBillDraftBestEffort(String.valueOf(encId));
            if (costaBillId != null) {
                meta.put("costa_bill_id", costaBillId);
            }
            return ResponseEntity.ok(Map.of(
                    "data", pct,
                    "meta", meta));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "INVALID_ENCOUNTER_ID", "message", "Encounter id must be the numeric PCT encounter id"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT completeEncounter failed: {}", e.getMessage());
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * Read-only billing visibility for a clinical encounter (Health OS: billing is
     * part of the care journey). Composes COSTA bills + payments for the encounter
     * into honest, derived states — never fabricates billing data and fail-closes
     * with {@code BILLING_UNAVAILABLE} when COSTA is unreachable.
     *
     * <p>Visibility only: all billing mutations remain on the FINANCE-role
     * {@code /internal/v1/finance/**} surface.</p>
     */
    @GetMapping("/{id}/billing")
    public ResponseEntity<Map<String, Object>> getEncounterBilling(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String encounterId = id.trim();
        try {
            JsonNode bills = costaClient.getBillsByEncounter(encounterId);
            List<Map<String, Object>> billResources = new ArrayList<>();
            String overallState = "NOT_BILLED";
            if (bills != null && bills.isArray()) {
                for (JsonNode bill : bills) {
                    billResources.add(toEncounterBillResource(bill));
                }
                for (Map<String, Object> resource : billResources) {
                    if (!"VOIDED".equals(resource.get("state"))) {
                        overallState = String.valueOf(resource.get("state"));
                        break;
                    }
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("encounter_id", encounterId);
            data.put("billing_state", overallState);
            data.put("bills", billResources);
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Encounter billing composition failed for {}: {}", encounterId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "BILLING_UNAVAILABLE",
                            "message", "Billing status is currently unavailable for this encounter"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /** Maps a COSTA bill (+ its payments) to an honest clinical billing resource. */
    private Map<String, Object> toEncounterBillResource(JsonNode bill) {
        Map<String, Object> resource = new LinkedHashMap<>();
        String billId = textOrNull(bill, "billId");
        String status = textOrNull(bill, "status");
        double totalPayable = doubleOrZero(bill, "totalPayable");
        double insurerPayable = doubleOrZero(bill, "insurerPayable");
        double patientPayable = doubleOrZero(bill, "patientPayable");
        double subsidyPayable = doubleOrZero(bill, "subsidyPayable");

        resource.put("bill_id", billId);
        resource.put("status", status);
        resource.put("bill_type", textOrNull(bill, "billType"));
        resource.put("currency", textOrNull(bill, "currency"));
        resource.put("total_charge", doubleOrZero(bill, "totalCharge"));
        resource.put("total_payable", totalPayable);
        resource.put("insurer_payable", insurerPayable);
        resource.put("patient_payable", patientPayable);
        resource.put("subsidy_payable", subsidyPayable);
        resource.put("write_off", doubleOrZero(bill, "writeOff"));
        resource.put("coverage_status", textOrNull(bill, "coverageStatus"));
        resource.put("coverage_plan_code", textOrNull(bill, "coveragePlanCode"));
        resource.put("created_at", textOrNull(bill, "createdAt"));
        resource.put("finalized_at", textOrNull(bill, "finalizedAt"));

        double paidTotal = 0.0;
        boolean anyPaymentPending = false;
        List<Map<String, Object>> paymentResources = new ArrayList<>();
        if (billId != null) {
            try {
                JsonNode payments = costaClient.getBillPayments(billId);
                if (payments != null && payments.isArray()) {
                    for (JsonNode payment : payments) {
                        String paymentStatus = textOrNull(payment, "status");
                        double paidAmount = doubleOrZero(payment, "paidAmount");
                        if ("PAID".equals(paymentStatus)) {
                            paidTotal += paidAmount > 0 ? paidAmount : doubleOrZero(payment, "amount");
                        } else if ("PENDING".equals(paymentStatus) || "AUTHORIZED".equals(paymentStatus)) {
                            anyPaymentPending = true;
                        }
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("id", textOrNull(payment, "id"));
                        p.put("payment_type", textOrNull(payment, "paymentType"));
                        p.put("amount", doubleOrZero(payment, "amount"));
                        p.put("paid_amount", paidAmount);
                        p.put("status", paymentStatus);
                        p.put("mushex_intent_id", textOrNull(payment, "mushexPaymentIntentId"));
                        p.put("paid_at", textOrNull(payment, "paidAt"));
                        paymentResources.add(p);
                    }
                }
            } catch (Exception e) {
                log.warn("Payments lookup failed for bill {}: {}", billId, e.getMessage());
                resource.put("payments_state", "UNAVAILABLE");
            }
        }
        resource.put("payments", paymentResources);
        resource.put("state", deriveBillState(status, patientPayable, paidTotal, anyPaymentPending));
        return resource;
    }

    /**
     * Honest, data-derived billing state. States mirror the finance doctrine
     * vocabulary; no state may claim payment success that the data does not show.
     */
    private static String deriveBillState(String status, double patientPayable,
                                          double paidTotal, boolean anyPaymentPending) {
        if (status == null) return "UNAVAILABLE";
        return switch (status) {
            case "VOID" -> "VOIDED";
            case "DRAFT", "ACCUMULATING" -> "BILLING_PENDING";
            case "APPROVAL_PENDING", "APPROVED" -> "BILL_GENERATED";
            case "FINAL" -> {
                if (patientPayable <= 0) yield "COVERED";
                if (paidTotal >= patientPayable) yield "PAID";
                if (anyPaymentPending) yield "PAYMENT_PENDING";
                yield "SHORTFALL_DUE";
            }
            default -> status;
        };
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String value = node.get(field).asText("");
        return value.isEmpty() ? null : value;
    }

    private static double doubleOrZero(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return 0.0;
        return node.get(field).asDouble(0.0);
    }

    @PostMapping("/{id}/pathway/sessions")
    public ResponseEntity<Map<String, Object>> startEncounterPathwaySession(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>(body != null ? body : Map.of());
        payload.putIfAbsent("encounter_id", id);
        try {
            JsonNode session = clinicalClient.startPathwaySession(payload);
            return ResponseEntity.ok(Map.of(
                    "data", session != null ? session : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("CKP start pathway session failed for encounter {}: {}", id, e.getMessage());
            return upstreamFailure("CKP_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/pathway/sessions/{sessionId}/advance")
    public ResponseEntity<Map<String, Object>> advanceEncounterPathwaySession(
            @PathVariable String id,
            @PathVariable UUID sessionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode advanced = clinicalClient.advancePathwaySession(sessionId, body != null ? body : Map.of());
            return ResponseEntity.ok(Map.of(
                    "data", advanced != null ? advanced : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId, "encounter_id", id)));
        } catch (Exception e) {
            log.warn("CKP advance pathway session failed for encounter {} session {}: {}", id, sessionId, e.getMessage());
            return upstreamFailure("CKP_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PatchMapping("/{id}/pathway-protocol")
    public ResponseEntity<Map<String, Object>> updateEncounterPathwayProtocol(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody UpdateEncounterPathwayProtocolRequest body) {
        try {
            long encounterId = Long.parseLong(id.trim());
            JsonNode pct = pctClient.updateEncounterPathwayProtocol(
                    encounterId, body.pathwayRef(), body.protocolRef());
            if (pct == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No encounter pathway/protocol payload returned", requestId, correlationId);
            }
            return ResponseEntity.ok(Map.of(
                    "data", pct,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "INVALID_ENCOUNTER_ID", "message", "Encounter id must be the numeric PCT encounter id"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/discharge")
    public ResponseEntity<Map<String, Object>> dischargeEncounter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            long encounterId = Long.parseLong(id.trim());
            String requestedJourneyId = body == null ? null : strVal(body, "journey_id", "journeyId");
            String dischargeType = body == null ? null : strVal(body, "discharge_type", "dischargeType", "outcome");
            JsonNode encounter = pctClient.getEncounter(encounterId);
            if (encounter == null || encounter.get("journeyId") == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "Encounter payload missing journey linkage", requestId, correlationId);
            }
            String journeyId = encounter.get("journeyId").asText();
            if (requestedJourneyId != null && !requestedJourneyId.isBlank() && !requestedJourneyId.equals(journeyId)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", Map.of("code", "ENCOUNTER_JOURNEY_MISMATCH", "message", "encounter_id and journey_id do not match"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }

            Map<String, Object> pctBody = new LinkedHashMap<>();
            pctBody.put("dischargeType", dischargeType != null ? dischargeType : "CLINICAL");
            if (body != null) {
                copyIfPresentCamel(body, pctBody, "discharge_diagnosis", "dischargeDiagnosis");
                copyIfPresentCamel(body, pctBody, "treatment_summary", "treatmentSummary");
                copyIfPresentCamel(body, pctBody, "follow_up_instructions", "followUpInstructions");
                copyIfPresentCamel(body, pctBody, "medications_at_discharge", "medicationsAtDischarge");
                copyIfPresentCamel(body, pctBody, "patient_instructions", "patientInstructions");
            }
            JsonNode discharge = pctClient.startDischarge(journeyId, pctBody);
            if (discharge == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No discharge payload returned", requestId, correlationId);
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            meta.put("journey_id", journeyId);
            meta.put("encounter_id", id.trim());
            meta.put("core_transaction_id", CoreTransactionCompositionService.encounterTransactionId(id.trim()));
            if (body != null) {
                copyIfPresent(body, meta, "discharge_diagnosis", "dischargeDiagnosis");
                copyIfPresent(body, meta, "follow_up_instructions", "followUpInstructions");
                copyIfPresent(body, meta, "patient_instructions", "patientInstructions");
            }
            // Same draft-only, non-blocking bill trigger as the mobile discharge path.
            String costaBillId = createBillDraftBestEffort(id.trim());
            if (costaBillId != null) {
                meta.put("costa_bill_id", costaBillId);
            }
            return ResponseEntity.ok(Map.of("data", discharge, "meta", meta));
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "INVALID_ENCOUNTER_ID", "message", "Encounter id must be the numeric PCT encounter id"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception ex) {
            return upstreamFailure("PCT_UNAVAILABLE", ex.getMessage(), requestId, correlationId);
        }
    }

    /**
     * Best-effort COSTA bill draft for a completed/discharged encounter.
     * Never blocks the clinical action: billing failure is logged, not surfaced
     * as a clinical error. Idempotency lives in COSTA ({@code BillService.createDraft}
     * returns the existing active bill for the encounter instead of duplicating).
     */
    private String createBillDraftBestEffort(String encounterId) {
        try {
            JsonNode billData = costaClient.createBillDraft(encounterId, "ENCOUNTER");
            if (billData != null && billData.has("billId")) {
                return billData.get("billId").asText();
            }
        } catch (Exception e) {
            log.warn("COSTA bill draft from web encounter close/discharge failed (non-blocking): {}", e.getMessage());
        }
        return null;
    }

    private static String extractEncounterId(JsonNode pctEncounter) {
        if (pctEncounter == null || pctEncounter.isNull()) {
            return null;
        }
        if (pctEncounter.has("id") && !pctEncounter.get("id").isNull()) {
            return pctEncounter.get("id").asText();
        }
        if (pctEncounter.has("encounterId") && !pctEncounter.get("encounterId").isNull()) {
            return pctEncounter.get("encounterId").asText();
        }
        JsonNode attributes = pctEncounter.path("attributes");
        if (attributes.has("id") && !attributes.get("id").isNull()) {
            return attributes.get("id").asText();
        }
        return null;
    }

    private static String strVal(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v.toString();
        }
        return null;
    }

    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String snakeKey, String camelKey) {
        Object value = from.get(snakeKey);
        if (value == null) {
            value = from.get(camelKey);
        }
        if (value != null && !value.toString().isBlank()) {
            to.put(snakeKey, value);
        }
    }

    private static void copyIfPresentCamel(Map<String, Object> from, Map<String, Object> to, String snakeKey, String camelKey) {
        Object value = from.get(snakeKey);
        if (value == null) {
            value = from.get(camelKey);
        }
        if (value != null && !value.toString().isBlank()) {
            to.put(camelKey, value);
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Encounter upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
