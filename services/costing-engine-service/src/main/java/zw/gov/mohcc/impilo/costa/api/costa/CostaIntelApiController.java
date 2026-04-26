package zw.gov.mohcc.impilo.costa.api.costa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.CostaTariffListEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffLibraryEntity;
import zw.gov.mohcc.impilo.costa.service.CostaTariffIntelService;
import zw.gov.mohcc.impilo.costa.service.CostEventCaptureService;
import zw.gov.mohcc.impilo.costa.service.tariff.TariffUploadService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.io.IOException;
import zw.gov.mohcc.impilo.costa.domain.entity.CostEventEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * COSTA tariff-library intelligence and settlement handoff API ({@code /api/costa/...}).
 * Complements legacy {@code /costa/v1/...} endpoints used by existing clients.
 */
@RestController
@RequestMapping("/api/costa")
public class CostaIntelApiController {

    private final CostaTariffIntelService intelService;
    private final TariffUploadService tariffUploadService;
    private final CostEventCaptureService costEventCaptureService;

    public CostaIntelApiController(CostaTariffIntelService intelService,
                                   TariffUploadService tariffUploadService,
                                   CostEventCaptureService costEventCaptureService) {
        this.intelService = intelService;
        this.tariffUploadService = tariffUploadService;
        this.costEventCaptureService = costEventCaptureService;
    }

    @GetMapping("/tariff-libraries")
    public ResponseEntity<ApiResponse<List<TariffLibraryEntity>>> listLibraries() {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(intelService.listLibraries(), ctx.correlationId().toString()));
    }

    @GetMapping("/tariff-lists")
    public ResponseEntity<ApiResponse<List<CostaTariffListEntity>>> listTariffLists() {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(intelService.listTariffLists(ctx.tenantId()), ctx.correlationId().toString()));
    }

    @GetMapping("/tariff-lists/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTariffList(@PathVariable("id") Long id) {
        var ctx = TrustContextHolder.require();
        CostaTariffListEntity list = intelService.requireTariffList(id, ctx.tenantId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tariff_list", list);
        payload.put("items", intelService.listTariffItems(id));
        return ResponseEntity.ok(ApiResponse.ok(payload, ctx.correlationId().toString()));
    }

    @PostMapping("/cost-estimate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> costEstimate(@RequestBody JsonNode body) {
        var ctx = TrustContextHolder.require();
        Map<String, Object> result = intelService.computeCostEstimate(body);
        return ResponseEntity.ok(ApiResponse.ok(result, ctx.correlationId().toString()));
    }

    @GetMapping("/billing-decisions/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBillingDecision(@PathVariable UUID id) {
        var ctx = TrustContextHolder.require();
        var bd = intelService.requireBillingDecision(id, ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("billing_decision", bd), ctx.correlationId().toString()));
    }

    @PostMapping("/charge-sheets")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createChargeSheet(@RequestBody JsonNode body) {
        var ctx = TrustContextHolder.require();
        var sheet = intelService.createChargeSheet(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("charge_sheet_id", sheet.getChargeSheetId()), ctx.correlationId().toString()));
    }

    @GetMapping("/charge-sheets/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChargeSheet(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(intelService.getChargeSheet(id), ctx.correlationId().toString()));
    }

    @PostMapping("/charge-sheets/{id}/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitChargeSheet(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        var sheet = intelService.submitChargeSheet(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("charge_sheet_id", sheet.getChargeSheetId(), "status", sheet.getStatus()),
                ctx.correlationId().toString()));
    }

    @PostMapping("/charge-sheets/{id}/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyChargeSheet(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        var sheet = intelService.verifyChargeSheet(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("charge_sheet_id", sheet.getChargeSheetId(), "status", sheet.getStatus()),
                ctx.correlationId().toString()));
    }

    @PostMapping("/invoices/from-cost-estimate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> invoiceFromEstimate(@RequestBody JsonNode body) {
        var ctx = TrustContextHolder.require();
        Map<String, Object> out = intelService.createInvoiceFromCostEstimate(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @PostMapping("/claims/from-invoice")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claimFromInvoice(@RequestBody JsonNode body) {
        var ctx = TrustContextHolder.require();
        Map<String, Object> out = intelService.createClaimFromInvoice(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @PostMapping("/payment-handoff")
    public ResponseEntity<ApiResponse<Map<String, Object>>> paymentHandoff(@RequestBody JsonNode body,
                                                                            HttpServletRequest request) {
        var ctx = TrustContextHolder.require();
        Map<String, Object> out = intelService.createPaymentHandoff(body, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @PostMapping("/tariff-lists/{id}/approve")
    public ResponseEntity<ApiResponse<CostaTariffListEntity>> approveTariffList(
            @PathVariable Long id,
            @RequestBody(required = false) JsonNode body) {
        var ctx = TrustContextHolder.require();
        JsonNode payload = body != null ? body : JsonNodeFactory.instance.objectNode();
        CostaTariffListEntity list = intelService.approveTariffList(id, payload);
        return ResponseEntity.ok(ApiResponse.ok(list, ctx.correlationId().toString()));
    }

    @PostMapping("/tariff-lists/{id}/retire")
    public ResponseEntity<ApiResponse<CostaTariffListEntity>> retireTariffList(@PathVariable Long id) {
        var ctx = TrustContextHolder.require();
        CostaTariffListEntity list = intelService.retireTariffList(id);
        return ResponseEntity.ok(ApiResponse.ok(list, ctx.correlationId().toString()));
    }

    @PostMapping("/tariff-upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> tariffUpload(@RequestBody JsonNode body) throws IOException {
        var ctx = TrustContextHolder.require();
        var batch = tariffUploadService.ingest(ctx.tenantId(), body, ctx.actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                Map.of("upload_batch_id", batch.getUploadBatchId().toString(), "upload", batch),
                ctx.correlationId().toString()));
    }

    @GetMapping("/tariff-upload/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTariffUpload(@PathVariable UUID id) {
        var ctx = TrustContextHolder.require();
        var batch = tariffUploadService.requireBatch(ctx.tenantId(), id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("upload", batch), ctx.correlationId().toString()));
    }

    @GetMapping("/tariff-upload/{id}/rows")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listTariffUploadRows(@PathVariable UUID id) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("rows", tariffUploadService.listRows(ctx.tenantId(), id)),
                ctx.correlationId().toString()));
    }

    @PutMapping("/tariff-upload/{id}/column-mapping")
    public ResponseEntity<ApiResponse<Map<String, Object>>> tariffUploadMapping(
            @PathVariable UUID id,
            @RequestBody JsonNode body) {
        var ctx = TrustContextHolder.require();
        var batch = tariffUploadService.setColumnMapping(ctx.tenantId(), id, body);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("upload", batch), ctx.correlationId().toString()));
    }

    @PostMapping("/tariff-upload/{id}/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> tariffUploadValidate(@PathVariable UUID id) {
        var ctx = TrustContextHolder.require();
        var batch = tariffUploadService.validate(ctx.tenantId(), id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("upload", batch), ctx.correlationId().toString()));
    }

    @PostMapping("/tariff-upload/{id}/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> tariffUploadSubmit(@PathVariable UUID id) {
        var ctx = TrustContextHolder.require();
        var batch = tariffUploadService.submit(ctx.tenantId(), id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("upload", batch), ctx.correlationId().toString()));
    }

    @PostMapping("/tariff-upload/{id}/approve-import")
    public ResponseEntity<ApiResponse<Map<String, Object>>> tariffUploadApproveImport(
            @PathVariable UUID id,
            @RequestBody(required = false) JsonNode body) {
        var ctx = TrustContextHolder.require();
        JsonNode payload = body != null ? body : JsonNodeFactory.instance.objectNode();
        Map<String, Object> out = tariffUploadService.approveImport(ctx.tenantId(), id, payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(out, ctx.correlationId().toString()));
    }

    @PostMapping("/cost-events")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCostEvent(@RequestBody JsonNode body) {
        var ctx = TrustContextHolder.require();
        var saved = costEventCaptureService.captureFromTrustApi(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                Map.of("cost_event_id", saved.getCostEventId(), "cost_event", saved),
                ctx.correlationId().toString()));
    }

    @GetMapping("/cost-events")
    public ResponseEntity<ApiResponse<List<CostEventEntity>>> listCostEvents(
            @RequestParam(required = false) String encounter_id,
            @RequestParam(required = false) String patient_cpid) {
        var ctx = TrustContextHolder.require();
        if (encounter_id != null && !encounter_id.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    costEventCaptureService.listForEncounter(ctx.tenantId(), encounter_id),
                    ctx.correlationId().toString()));
        }
        if (patient_cpid != null && !patient_cpid.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    costEventCaptureService.listForPatient(ctx.tenantId(), patient_cpid),
                    ctx.correlationId().toString()));
        }
        throw new IllegalArgumentException("Provide encounter_id or patient_cpid");
    }
}
