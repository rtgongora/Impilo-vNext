package zw.gov.mohcc.impilo.oros.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.HistopathReportRequest;
import zw.gov.mohcc.impilo.oros.api.dto.NoteRequest;
import zw.gov.mohcc.impilo.oros.core.HistopathReportService;
import zw.gov.mohcc.impilo.oros.persistence.entity.HistopathReportEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;

/**
 * REST controller for histopathology report depth (spec §11).
 *
 * <p>Order-scoped: a DRAFT is captured/updated ({@code POST /v1/orders/{orderId}/histopath}), signed
 * out by the pathologist ({@code .../sign-out} — which also produces the canonical FINAL result +
 * SHR DiagnosticReport + HL7 ORU via the reporting lifecycle), acknowledged
 * ({@code .../acknowledge}), and read back ({@code GET .../histopath}). Consumed by the inpatient
 * theatre client.</p>
 */
@RestController
@RequestMapping("/v1")
public class HistopathReportController {

    private final HistopathReportService service;

    public HistopathReportController(HistopathReportService service) {
        this.service = service;
    }

    @PostMapping("/orders/{orderId}/histopath")
    public ResponseEntity<ApiResponse<Map<String, Object>>> draft(
            @PathVariable String orderId, @RequestBody(required = false) HistopathReportRequest request) {
        HistopathReportEntity r = service.draft(orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(HistopathReportService.toMap(r), correlationId()));
    }

    @PostMapping("/orders/{orderId}/histopath/sign-out")
    public ResponseEntity<ApiResponse<Map<String, Object>>> signOut(
            @PathVariable String orderId, @RequestBody(required = false) HistopathReportRequest request) {
        HistopathReportEntity r = service.signOut(orderId, request);
        return ResponseEntity.ok(ApiResponse.ok(HistopathReportService.toMap(r), correlationId()));
    }

    @PostMapping("/orders/{orderId}/histopath/acknowledge")
    public ResponseEntity<ApiResponse<Map<String, Object>>> acknowledge(
            @PathVariable String orderId, @RequestBody(required = false) NoteRequest request) {
        HistopathReportEntity r = service.acknowledge(orderId, request != null ? request.note() : null);
        return ResponseEntity.ok(ApiResponse.ok(HistopathReportService.toMap(r), correlationId()));
    }

    @GetMapping("/orders/{orderId}/histopath")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable String orderId) {
        Map<String, Object> report = service.get(orderId);
        return ResponseEntity.ok(ApiResponse.ok(report, correlationId()));
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
