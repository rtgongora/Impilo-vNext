package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.coverage.api.dto.AdvancedDtos.*;
import zw.gov.mohcc.impilo.coverage.core.EmployerService;
import zw.gov.mohcc.impilo.coverage.core.EmployerService.RosterRowInput;

import java.util.List;
import java.util.UUID;

/** Employer / group administration + roster stage->validate->apply (spec §24). */
@RestController
@RequestMapping("/internal/v1/coverage/employers")
public class EmployerController {

    private final EmployerService service;

    public EmployerController(EmployerService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<EmployerView>> list(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listEmployers(UUID.fromString(tenantId)).stream().map(EmployerView::of).toList());
    }

    @PostMapping
    public ResponseEntity<EmployerView> register(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @Valid @RequestBody RegisterEmployerRequest req) {
        var e = service.register(UUID.fromString(tenantId), podId, req.employerCode().trim(), req.name().trim(),
                req.contractNumber(), req.payerRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(EmployerView.of(e));
    }

    @GetMapping("/{id}/roster-batches")
    public ResponseEntity<List<RosterBatchView>> batches(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(service.batches(UUID.fromString(tenantId), id).stream().map(RosterBatchView::of).toList());
    }

    @PostMapping("/{id}/roster-batches")
    public ResponseEntity<RosterBatchView> stage(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @PathVariable UUID id, @Valid @RequestBody StageRosterRequest req) {
        List<RosterRowInput> rows = req.rows() == null ? List.of() : req.rows().stream()
                .map(r -> new RosterRowInput(r.clientId(), r.memberNumber(), r.relationship())).toList();
        var batch = service.stage(UUID.fromString(tenantId), podId, id, req.planId(), req.uploadedBy(), rows);
        return ResponseEntity.status(HttpStatus.CREATED).body(RosterBatchView.of(batch));
    }

    @GetMapping("/roster-batches/{batchId}")
    public ResponseEntity<RosterBatchView> batch(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID batchId) {
        return ResponseEntity.ok(RosterBatchView.of(service.batch(UUID.fromString(tenantId), batchId)));
    }

    @GetMapping("/roster-batches/{batchId}/rows")
    public ResponseEntity<List<RosterRowView>> rows(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID batchId) {
        service.batch(UUID.fromString(tenantId), batchId); // tenant guard
        return ResponseEntity.ok(service.rows(batchId).stream().map(RosterRowView::of).toList());
    }

    @PostMapping("/roster-batches/{batchId}/validate")
    public ResponseEntity<RosterBatchView> validate(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID batchId) {
        return ResponseEntity.ok(RosterBatchView.of(service.validate(UUID.fromString(tenantId), batchId)));
    }

    @PostMapping("/roster-batches/{batchId}/apply")
    public ResponseEntity<RosterBatchView> apply(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID batchId) {
        return ResponseEntity.ok(RosterBatchView.of(service.apply(UUID.fromString(tenantId), podId, batchId,
                CorrelationIds.fromHeader(correlationId))));
    }
}
