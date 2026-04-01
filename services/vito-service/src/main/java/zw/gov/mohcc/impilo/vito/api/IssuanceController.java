package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.vito.core.*;
import zw.gov.mohcc.impilo.vito.core.IdentityService.ClientRegistrationResult;
import zw.gov.mohcc.impilo.vito.core.issuance.IssuanceStateMachineService;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Internal issuance workflow endpoints.
 * Used by operators to manage the Health-ID issuance pipeline.
 *
 * All responses follow the ApiResponse<T> envelope pattern and use
 * IssuanceRequestDto to expose spec-compliant field names.
 */
@RestController
@RequestMapping("/v1/internal/issuance")
public class IssuanceController {

    private final IssuanceStateMachineService issuanceService;
    private final IdentityService identityService;
    private final ClientRepository clientRepo;

    public IssuanceController(IssuanceStateMachineService issuanceService,
                               IdentityService identityService,
                               ClientRepository clientRepo) {
        this.issuanceService = issuanceService;
        this.identityService = identityService;
        this.clientRepo = clientRepo;
    }

    record IssuanceSubmitPayload(
            String givenName, String familyName, String dateOfBirth,
            String gender, String nationalId, String phoneNumber,
            String facilityId, String source
    ) {}

    record ProofingEventPayload(
            String method, String outcome, Double confidence,
            String performedAt, String performedBy, String notes
    ) {}

    record DeliverPayload(String deliveryChannel, String deliveredAt) {}

    record IssuanceRequestDto(
            String requestId,
            String healthId,
            String status,
            String applicantName,
            String dateOfBirth,
            String facilityId,
            String type,
            String channel,
            String rejectionReason,
            String impiloIdIssued,
            String actorId,
            OffsetDateTime submittedAt,
            OffsetDateTime approvedAt,
            OffsetDateTime issuedAt,
            OffsetDateTime deliveredAt,
            OffsetDateTime rejectedAt
    ) {}

    private IssuanceRequestDto toDto(IssuanceRequestEntity req) {
        String applicantName = null;
        String dob = null;
        Optional<ClientEntity> clientOpt = clientRepo.findByTenantIdAndHealthId(
                req.getTenantId(), req.getHealthId());
        if (clientOpt.isPresent()) {
            ClientEntity c = clientOpt.get();
            String given = c.getGivenName() != null ? c.getGivenName() : "";
            String family = c.getFamilyName() != null ? c.getFamilyName() : "";
            applicantName = (given + " " + family).trim();
            dob = c.getDateOfBirth() != null ? c.getDateOfBirth().toString() : null;
        }
        return new IssuanceRequestDto(
                req.getId().toString(),
                req.getHealthId().toString(),
                req.getState().name(),
                applicantName,
                dob,
                null,
                req.getType() != null ? req.getType().name() : null,
                req.getChannel() != null ? req.getChannel().name() : null,
                req.getRejectionReason(),
                req.getImpiloIdIssued(),
                req.getAssignedTo(),
                req.getSubmittedAt(),
                req.getApprovedAt(),
                req.getIssuedAt(),
                req.getDeliveredAt(),
                req.getRejectedAt()
        );
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<IssuanceRequestDto>> submit(
            @RequestBody IssuanceSubmitPayload body) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);
        UUID tenantId = ctx.tenantId();

        ClientRegistrationResult reg = identityService.register(
                tenantId,
                body.givenName(),
                body.familyName(),
                body.dateOfBirth(),
                body.gender(),
                ctx.actorId());

        UUID healthId = reg.client().getHealthId();
        IssuanceRequestEntity req = issuanceService.submit(
                tenantId, healthId, IssuanceType.NEW, IssuanceChannel.ASSISTED, ctx.actorId());

        return ResponseEntity.status(201)
                .body(ApiResponse.ok(toDto(req), ctx.correlationId().toString()));
    }

    @PostMapping("/{requestId}/proofing")
    public ResponseEntity<ApiResponse<IssuanceRequestDto>> startProofing(
            @PathVariable Long requestId,
            @RequestBody(required = false) ProofingEventPayload body) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);
        UUID tenantId = ctx.tenantId();
        IssuanceRequestEntity req = issuanceService.startProofing(tenantId, requestId, ctx.actorId());
        return ResponseEntity.ok(ApiResponse.ok(toDto(req), ctx.correlationId().toString()));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ApiResponse<IssuanceRequestDto>> approve(
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);
        UUID tenantId = ctx.tenantId();
        IssuanceRequestEntity req = issuanceService.approve(tenantId, requestId, ctx.actorId());
        return ResponseEntity.ok(ApiResponse.ok(toDto(req), ctx.correlationId().toString()));
    }

    @PostMapping("/{requestId}/issue")
    public ResponseEntity<ApiResponse<IssuanceRequestDto>> issue(@PathVariable Long requestId) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);
        UUID tenantId = ctx.tenantId();
        IssuanceRequestEntity req = issuanceService.issue(tenantId, requestId, ctx.actorId());
        return ResponseEntity.ok(ApiResponse.ok(toDto(req), ctx.correlationId().toString()));
    }

    @PostMapping("/{requestId}/deliver")
    public ResponseEntity<ApiResponse<IssuanceRequestDto>> deliver(
            @PathVariable Long requestId,
            @RequestBody(required = false) DeliverPayload body) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);
        UUID tenantId = ctx.tenantId();
        String channel = body != null ? body.deliveryChannel() : null;
        IssuanceRequestEntity req = issuanceService.deliver(tenantId, requestId, channel);
        return ResponseEntity.ok(ApiResponse.ok(toDto(req), ctx.correlationId().toString()));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ApiResponse<IssuanceRequestDto>> reject(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        requireInternal(ctx);
        UUID tenantId = ctx.tenantId();
        IssuanceRequestEntity req = issuanceService.reject(tenantId, requestId, body.get("reason"));
        return ResponseEntity.ok(ApiResponse.ok(toDto(req), ctx.correlationId().toString()));
    }

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<PagedResponse<IssuanceRequestDto>>> queue(
            @RequestParam(defaultValue = "SUBMITTED") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        IssuanceState issuanceState = IssuanceState.valueOf(status);
        Page<IssuanceRequestEntity> result = issuanceService.listByState(
                tenantId, issuanceState, PageRequest.of(page, size));
        List<IssuanceRequestDto> dtos = result.getContent().stream().map(this::toDto).toList();
        PagedResponse<IssuanceRequestDto> paged = PagedResponse.of(dtos, page, size, result.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ApiResponse<IssuanceRequestDto>> get(@PathVariable Long requestId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        IssuanceRequestEntity req = issuanceService.getRequest(tenantId, requestId);
        return ResponseEntity.ok(ApiResponse.ok(toDto(req), ctx.correlationId().toString()));
    }

    private void requireInternal(TrustContext ctx) {
        if (ctx.mode() == AccessMode.EXTERNAL) {
            throw new SecurityException("Issuance operations are internal only");
        }
    }
}
