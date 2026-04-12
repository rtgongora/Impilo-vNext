package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.CreateRemittanceRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.RemittanceResponse;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.RemittanceEntity;
import zw.gov.mohcc.impilo.coverage.repository.RemittanceRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provider remittance/payout record endpoint (internal).
 */
@RestController
@RequestMapping("/internal/v1/coverage/remittances")
public class RemittanceController {

    private final RemittanceRepository remittanceRepository;
    private final CoverageEventService eventService;

    public RemittanceController(RemittanceRepository remittanceRepository,
                                CoverageEventService eventService) {
        this.remittanceRepository = remittanceRepository;
        this.eventService = eventService;
    }

    /**
     * Create a remittance/payout record (internal).
     */
    @PostMapping
    @Transactional
    public ResponseEntity<RemittanceResponse> createRemittance(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @Valid @RequestBody CreateRemittanceRequest request) {

        UUID tid = UUID.fromString(tenantId);

        RemittanceEntity remittance = new RemittanceEntity(
                tid, podId, request.payerId(), request.providerRef(),
                request.amount(), request.currency(),
                request.referenceNumber(), request.lineItems(), request.notes());

        remittanceRepository.save(remittance);

        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("payer_id", request.payerId());
        payload.put("provider_ref", request.providerRef());
        payload.put("amount", request.amount().toPlainString());
        payload.put("status", "PENDING");
        UUID corr = CorrelationIds.fromHeader(correlationId);
        payload.put("meta", CoverageEventService.meta(corr));
        eventService.emitRemittanceCreated(remittance.getId(), corr, tid, podId,
                remittance.getId().toString(), payload);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(remittance));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RemittanceResponse> getRemittance(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        return remittanceRepository.findByIdAndTenantId(id, UUID.fromString(tenantId))
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<RemittanceResponse>> listRemittances(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        UUID tid = UUID.fromString(tenantId);
        List<RemittanceResponse> remittances = remittanceRepository.findAll()
                .stream()
                .filter(r -> r.getTenantId().equals(tid))
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(remittances);
    }

    private RemittanceResponse toResponse(RemittanceEntity e) {
        return new RemittanceResponse(
                e.getId(), e.getPayerId(), e.getProviderRef(),
                e.getAmount(), e.getCurrency(), e.getStatus(),
                e.getReferenceNumber(), e.getCreatedAt());
    }

}
