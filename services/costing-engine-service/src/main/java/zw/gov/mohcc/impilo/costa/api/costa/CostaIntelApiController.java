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
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiError;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * COSTA tariff-library intelligence and settlement handoff API ({@code /api/costa/...}).
 * Complements legacy {@code /costa/v1/...} endpoints used by existing clients.
 */
@RestController
@RequestMapping("/api/costa")
public class CostaIntelApiController {

    private final CostaTariffIntelService intelService;

    public CostaIntelApiController(CostaTariffIntelService intelService) {
        this.intelService = intelService;
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
    public ResponseEntity<ApiResponse<Void>> tariffUploadStub() {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(ApiResponse.error(
                new ApiError("NOT_IMPLEMENTED", "Tariff upload (CSV/XLSX/JSON) pipeline is deferred.", 501),
                ctx.correlationId().toString()));
    }

    @PostMapping("/tariff-upload/{id}/validate")
    public ResponseEntity<ApiResponse<Void>> tariffUploadValidateStub(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(ApiResponse.error(
                new ApiError("NOT_IMPLEMENTED", "Tariff upload validation is deferred (batch " + id + ").", 501),
                ctx.correlationId().toString()));
    }

    @PostMapping("/tariff-upload/{id}/submit")
    public ResponseEntity<ApiResponse<Void>> tariffUploadSubmitStub(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(ApiResponse.error(
                new ApiError("NOT_IMPLEMENTED", "Tariff upload submit is deferred (batch " + id + ").", 501),
                ctx.correlationId().toString()));
    }
}
