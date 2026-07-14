package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.ControlledDrugEntryDto;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordControlledDrugRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordControlledDrugResponse;
import zw.gov.mohcc.impilo.inventory.core.ControlledDrugRegisterService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlled-drug witness register REST API ({@code /v1/internal/controlled-drugs}).
 *
 * <p>Internal service-to-service surface used by inpatient/theatre clients to
 * record controlled-drug issue/administer/waste/discard with witness sign-off.</p>
 */
@RestController
@RequestMapping("/v1/internal/controlled-drugs")
public class ControlledDrugController {

    private static final Logger log = LoggerFactory.getLogger(ControlledDrugController.class);

    private final ControlledDrugRegisterService registerService;

    public ControlledDrugController(ControlledDrugRegisterService registerService) {
        this.registerService = registerService;
    }

    @PostMapping("/record")
    public ResponseEntity<ApiResponse<RecordControlledDrugResponse>> record(
            @Valid @RequestBody RecordControlledDrugRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        RecordControlledDrugResponse response = registerService.record(request);
        log.info("Controlled-drug entry recorded via API: item={}, action={}",
                request.itemCode(), request.action());
        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ControlledDrugEntryDto>>> list(
            @RequestParam(value = "refType", required = false) String refType,
            @RequestParam(value = "refId", required = false) String refId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ControlledDrugEntryDto> dtos = registerService.list(refType, refId).stream()
                .map(ControlledDrugEntryDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> balance(
            @RequestParam("itemCode") String itemCode,
            @RequestParam(value = "refId", required = false) String refId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        BigDecimal balance = registerService.currentBalance(itemCode, refId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemCode", itemCode);
        payload.put("refId", refId);
        payload.put("runningBalance", balance);
        return ResponseEntity.ok(ApiResponse.ok(payload, correlationId));
    }
}
