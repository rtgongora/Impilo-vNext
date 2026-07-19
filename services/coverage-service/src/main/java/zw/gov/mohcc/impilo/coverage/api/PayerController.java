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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.CreatePayerRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.PayerResponse;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.PayerEntity;
import zw.gov.mohcc.impilo.coverage.repository.PayerRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payer Registry endpoints (spec §8). A national registry of organisations that
 * may financially cover care. A SUSPENDED payer blocks new enrolments (enforced
 * in the enrolment path) but keeps historic transactions accessible.
 */
@RestController
@RequestMapping("/internal/v1/coverage/payers")
public class PayerController {

    private final PayerRepository payerRepository;
    private final CoverageEventService eventService;

    public PayerController(PayerRepository payerRepository, CoverageEventService eventService) {
        this.payerRepository = payerRepository;
        this.eventService = eventService;
    }

    @GetMapping
    public ResponseEntity<List<PayerResponse>> list(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "status", required = false) String status) {
        UUID tid = UUID.fromString(tenantId);
        List<PayerEntity> rows = (status != null && !status.isBlank())
                ? payerRepository.findByTenantIdAndStatus(tid, status.trim().toUpperCase())
                : payerRepository.findByTenantId(tid);
        return ResponseEntity.ok(rows.stream().map(PayerResponse::of).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayerResponse> get(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        return payerRepository.findByIdAndTenantId(id, UUID.fromString(tenantId))
                .map(PayerResponse::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<PayerResponse> create(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody CreatePayerRequest req) {
        UUID tid = UUID.fromString(tenantId);
        payerRepository.findByTenantIdAndPayerCode(tid, req.payerCode().trim()).ifPresent(p -> {
            throw new IllegalArgumentException("Payer code already exists: " + req.payerCode());
        });
        PayerEntity payer = PayerEntity.create(tid, podId, req.payerCode().trim(), req.legalName().trim(),
                req.tradingName(), req.organisationType(), req.integrationMethod());
        payer.setSettlementReference(req.settlementReference());
        payer.setRegulatoryStatus(req.regulatoryStatus());
        payer.setSourceAuthority(req.sourceAuthority());
        payer.setApprovedBy("system");
        payer.setApprovedAt(OffsetDateTime.now());
        payerRepository.save(payer);

        UUID corr = CorrelationIds.fromHeader(correlationId);
        eventService.emitPayer("created", payer.getId(), corr, tid, podId, snapshot(payer));
        return ResponseEntity.status(HttpStatus.CREATED).body(PayerResponse.of(payer));
    }

    @PostMapping("/{id}/suspend")
    @Transactional
    public ResponseEntity<PayerResponse> suspend(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id) {
        return transition(tenantId, podId, correlationId, id, "SUSPENDED", "suspended");
    }

    @PostMapping("/{id}/reactivate")
    @Transactional
    public ResponseEntity<PayerResponse> reactivate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id) {
        return transition(tenantId, podId, correlationId, id, "ACTIVE", "updated");
    }

    private ResponseEntity<PayerResponse> transition(String tenantId, String podId, String correlationId,
                                                     UUID id, String newStatus, String action) {
        UUID tid = UUID.fromString(tenantId);
        PayerEntity payer = payerRepository.findByIdAndTenantId(id, tid)
                .orElseThrow(() -> new IllegalArgumentException("Payer not found: " + id));
        if ("TERMINATED".equals(payer.getStatus())) {
            throw new IllegalArgumentException("Terminated payer cannot change status");
        }
        payer.setStatus(newStatus);
        payer.touch();
        payerRepository.save(payer);
        UUID corr = CorrelationIds.fromHeader(correlationId);
        eventService.emitPayer(action, payer.getId(), corr, tid, podId, snapshot(payer));
        return ResponseEntity.ok(PayerResponse.of(payer));
    }

    private static Map<String, Object> snapshot(PayerEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("payer_code", p.getPayerCode());
        m.put("legal_name", p.getLegalName());
        m.put("organisation_type", p.getOrganisationType());
        m.put("status", p.getStatus());
        return m;
    }
}
