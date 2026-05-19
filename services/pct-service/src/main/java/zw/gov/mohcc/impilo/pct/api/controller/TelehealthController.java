package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.PctDomainException;
import zw.gov.mohcc.impilo.pct.core.TelehealthSessionService;
import zw.gov.mohcc.impilo.pct.persistence.entity.TelehealthSessionEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class TelehealthController {
    private final TelehealthSessionService telehealthService;

    public TelehealthController(TelehealthSessionService telehealthService) {
        this.telehealthService = telehealthService;
    }

    @PostMapping("/telehealth")
    public ResponseEntity<ApiResponse<TelehealthSessionEntity>> create(@RequestBody Map<String, Object> request) {
        return execute(() -> telehealthService.createSession(request));
    }

    @GetMapping("/telehealth/{id}")
    public ResponseEntity<ApiResponse<TelehealthSessionEntity>> get(@PathVariable UUID id) {
        return execute(() -> telehealthService.getSession(id));
    }

    @PostMapping("/telehealth/{id}/join")
    public ResponseEntity<ApiResponse<TelehealthSessionEntity>> join(@PathVariable UUID id) {
        return execute(() -> telehealthService.join(id));
    }

    @PostMapping("/telehealth/{id}/end")
    public ResponseEntity<ApiResponse<TelehealthSessionEntity>> end(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        String notes = body == null ? null : (body.get("notes") == null ? null : body.get("notes").toString());
        return execute(() -> telehealthService.end(id, notes));
    }

    @GetMapping("/patient/{cpid}/telehealth")
    public ResponseEntity<ApiResponse<List<TelehealthSessionEntity>>> listPatient(
            @PathVariable String cpid,
            @RequestParam(required = false) String status) {
        return executeList(() -> telehealthService.listByPatient(cpid, status));
    }

    @GetMapping("/telehealth")
    public ResponseEntity<ApiResponse<List<TelehealthSessionEntity>>> listFacility(
            @RequestParam String facilityId,
            @RequestParam(required = false) String status) {
        return executeList(() -> telehealthService.listByFacility(facilityId, status));
    }

    private ResponseEntity<ApiResponse<TelehealthSessionEntity>> execute(ThrowingSupplier<TelehealthSessionEntity> action) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        try {
            return ResponseEntity.ok(ApiResponse.ok(action.get(), correlationId));
        } catch (PctDomainException ex) {
            return ResponseEntity.status(ex.getStatus())
                    .body(ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getStatus(), correlationId));
        }
    }

    private ResponseEntity<ApiResponse<List<TelehealthSessionEntity>>> executeList(ThrowingSupplier<List<TelehealthSessionEntity>> action) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        try {
            return ResponseEntity.ok(ApiResponse.ok(action.get(), correlationId));
        } catch (PctDomainException ex) {
            return ResponseEntity.status(ex.getStatus())
                    .body(ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getStatus(), correlationId));
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }
}
