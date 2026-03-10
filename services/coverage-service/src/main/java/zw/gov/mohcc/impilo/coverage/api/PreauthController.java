package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.CreatePreauthRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.PreauthDecisionRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.PreauthResponse;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.PreauthRequestEntity;
import zw.gov.mohcc.impilo.coverage.repository.PreauthRequestRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Pre-authorization creation and decision endpoint (internal).
 *
 * Stores decision_evidence_json for staleness evidence per v1.1 Law 6.
 */
@RestController
@RequestMapping("/internal/v1/coverage/preauth")
public class PreauthController {

    private final PreauthRequestRepository preauthRepository;
    private final CoverageEventService eventService;

    public PreauthController(PreauthRequestRepository preauthRepository,
                             CoverageEventService eventService) {
        this.preauthRepository = preauthRepository;
        this.eventService = eventService;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<PreauthResponse> createPreauth(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @Valid @RequestBody CreatePreauthRequest request) {

        UUID tid = UUID.fromString(tenantId);

        PreauthRequestEntity preauth = new PreauthRequestEntity(
                tid, podId, request.coverageId(),
                request.facilityId(), request.providerId(),
                request.requestType(), request.clinicalInfo(),
                request.requestedItems());

        preauthRepository.save(preauth);

        eventService.emitCreated("preauth", preauth.getId().toString(),
                parseUuid(correlationId), tid, podId,
                Map.of("coverage_id", request.coverageId().toString(),
                        "request_type", request.requestType(),
                        "status", "PENDING"));

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(preauth));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreauthResponse> getPreauth(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        return preauthRepository.findByIdAndTenantId(id, UUID.fromString(tenantId))
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Decision endpoint — approve or deny a pre-authorization.
     */
    @PutMapping("/{id}/decision")
    @Transactional
    public ResponseEntity<PreauthResponse> decidePreauth(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @PathVariable UUID id,
            @Valid @RequestBody PreauthDecisionRequest request) {

        UUID tid = UUID.fromString(tenantId);

        PreauthRequestEntity preauth = preauthRepository.findByIdAndTenantId(id, tid)
                .orElse(null);
        if (preauth == null) {
            return ResponseEntity.notFound().build();
        }

        if (!"PENDING".equals(preauth.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        preauth.decide(request.status(), request.decisionJson(), request.decisionEvidenceJson());
        preauthRepository.save(preauth);

        String action = "APPROVED".equals(request.status()) ? "approved" : "denied";
        eventService.emitStatusChange("preauth", preauth.getId().toString(), action,
                parseUuid(correlationId), tid, podId,
                Map.of("status", Map.of("old", "PENDING", "new", request.status()),
                        "meta", Map.of("decision", Map.of("evidence", "bounded-stale-class-b"))));

        return ResponseEntity.ok(toResponse(preauth));
    }

    private PreauthResponse toResponse(PreauthRequestEntity e) {
        return new PreauthResponse(
                e.getId(), e.getCoverageId(), e.getFacilityId(),
                e.getProviderId(), e.getRequestType(), e.getStatus(),
                e.getRequestedItems(), e.getDecisionJson(),
                e.getRequestedAt(), e.getDecidedAt(), e.getExpiresAt());
    }

    private UUID parseUuid(String s) {
        try { return s != null ? UUID.fromString(s) : null; }
        catch (IllegalArgumentException e) { return null; }
    }
}
