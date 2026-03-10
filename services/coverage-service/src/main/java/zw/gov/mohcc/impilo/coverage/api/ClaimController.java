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
import zw.gov.mohcc.impilo.coverage.api.dto.ClaimResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.SubmitClaimRequest;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.ClaimEntity;
import zw.gov.mohcc.impilo.coverage.repository.ClaimRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Claim submission and status query endpoints (internal).
 */
@RestController
@RequestMapping("/internal/v1/coverage/claims")
public class ClaimController {

    private final ClaimRepository claimRepository;
    private final CoverageEventService eventService;

    public ClaimController(ClaimRepository claimRepository,
                           CoverageEventService eventService) {
        this.claimRepository = claimRepository;
        this.eventService = eventService;
    }

    /**
     * Claim submission endpoint (internal).
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ClaimResponse> submitClaim(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @Valid @RequestBody SubmitClaimRequest request) {

        UUID tid = UUID.fromString(tenantId);

        ClaimEntity claim = new ClaimEntity(
                tid, podId, request.coverageId(), request.preauthId(),
                request.facilityId(), request.providerId(), request.encounterId(),
                request.claimType(), request.lineItems(), request.totalAmount());

        claimRepository.save(claim);

        eventService.emitCreated("claim", claim.getId().toString(),
                parseUuid(correlationId), tid, podId,
                Map.of("coverage_id", request.coverageId().toString(),
                        "claim_type", request.claimType(),
                        "total_amount", request.totalAmount().toString(),
                        "status", "SUBMITTED"));

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(claim));
    }

    /**
     * Claim status query endpoint (internal).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ClaimResponse> getClaimStatus(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        return claimRepository.findByIdAndTenantId(id, UUID.fromString(tenantId))
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List claims by coverage (internal).
     */
    @GetMapping
    public ResponseEntity<List<ClaimResponse>> listClaims(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam UUID coverageId) {
        UUID tid = UUID.fromString(tenantId);
        List<ClaimResponse> claims = claimRepository.findByTenantIdAndCoverageId(tid, coverageId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(claims);
    }

    private ClaimResponse toResponse(ClaimEntity e) {
        return new ClaimResponse(
                e.getId(), e.getCoverageId(), e.getPreauthId(),
                e.getFacilityId(), e.getProviderId(), e.getEncounterId(),
                e.getClaimType(), e.getStatus(), e.getTotalAmount(),
                e.getApprovedAmount(), e.getSubmittedAt(), e.getAdjudicatedAt());
    }

    private UUID parseUuid(String s) {
        try { return s != null ? UUID.fromString(s) : null; }
        catch (IllegalArgumentException e) { return null; }
    }
}
