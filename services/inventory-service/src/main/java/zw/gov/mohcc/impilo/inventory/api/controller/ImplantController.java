package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.ImplantTraceDto;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordImplantRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordImplantResponse;
import zw.gov.mohcc.impilo.inventory.core.ImplantTraceabilityService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

/**
 * Implant UDI / serial traceability REST API ({@code /v1/internal/implants}).
 *
 * <p>Internal service-to-service surface used by the inpatient/theatre clients
 * to register implanted devices and to run recall traces.</p>
 */
@RestController
@RequestMapping("/v1/internal/implants")
public class ImplantController {

    private static final Logger log = LoggerFactory.getLogger(ImplantController.class);

    private final ImplantTraceabilityService implantService;

    public ImplantController(ImplantTraceabilityService implantService) {
        this.implantService = implantService;
    }

    @PostMapping("/record")
    public ResponseEntity<ApiResponse<RecordImplantResponse>> record(
            @Valid @RequestBody RecordImplantRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        RecordImplantResponse response = implantService.recordImplant(request);
        log.info("Implant recorded via API: unit={}, patient={}", response.implantUnitId(), request.patientCpid());
        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    @GetMapping("/recall")
    public ResponseEntity<ApiResponse<List<ImplantTraceDto>>> recall(
            @RequestParam(value = "udi", required = false) String udi,
            @RequestParam(value = "lot", required = false) String lot) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ImplantTraceDto> traces = implantService.traceByRecall(udi, lot);
        return ResponseEntity.ok(ApiResponse.ok(traces, correlationId));
    }

    @GetMapping("/patient/{cpid}")
    public ResponseEntity<ApiResponse<List<ImplantTraceDto>>> byPatient(@PathVariable("cpid") String cpid) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ImplantTraceDto> traces = implantService.listByPatient(cpid);
        return ResponseEntity.ok(ApiResponse.ok(traces, correlationId));
    }

    @GetMapping("/unit/{udi}")
    public ResponseEntity<ApiResponse<List<ImplantTraceDto>>> byUnit(@PathVariable("udi") String udi) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ImplantTraceDto> traces = implantService.traceByUdi(udi);
        return ResponseEntity.ok(ApiResponse.ok(traces, correlationId));
    }
}
