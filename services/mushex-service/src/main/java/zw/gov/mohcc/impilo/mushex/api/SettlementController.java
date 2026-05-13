package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.SettlementRunRequest;
import zw.gov.mohcc.impilo.mushex.service.SettlementService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mushex/v1/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<Object>> runSettlement(
            @Valid @RequestBody SettlementRunRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Object settlement = settlementService.runSettlement(
                ctx.tenantId(),
                request.periodStart(),
                request.periodEnd()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(settlement, correlationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getSettlement(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Object settlement = settlementService.getSettlement(id);

        return ResponseEntity.ok(ApiResponse.ok(settlement, correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Object>>> listSettlements(
            @RequestParam(name = "intent_id", required = false) String intentId,
            @RequestParam(name = "intent_ids", required = false) List<String> intentIds) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        List<Object> rows = (intentIds != null && !intentIds.isEmpty()
                ? settlementService.listSettlements(intentIds)
                : settlementService.listSettlements(intentId)).stream()
                .map(r -> (Object) r)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(rows, correlationId));
    }

    @PostMapping("/{id}/release-payouts")
    public ResponseEntity<ApiResponse<Object>> releasePayouts(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Object settlement = settlementService.releasePayouts(id);

        return ResponseEntity.ok(ApiResponse.ok(settlement, correlationId));
    }
}
