package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimAckRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimAdjudicationRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimAttachmentRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimCreateRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimDisputeRequest;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimAttachmentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEventEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.ClaimStatus;
import zw.gov.mohcc.impilo.mushex.service.ClaimService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/mushex/v1/claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ClaimEntity>>> listClaims(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) String insurerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Pageable pageable = PageRequest.of(page, size);
        Page<ClaimEntity> result = claimService.listClaims(ctx.tenantId(), status, insurerId, pageable);

        PagedResponse<ClaimEntity> paged = PagedResponse.of(
                result.getContent(), page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ClaimEntity>> createClaim(
            @Valid @RequestBody ClaimCreateRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        UUID facilityId = request.facilityId() != null
                ? UUID.fromString(request.facilityId())
                : ctx.facilityId();

        ClaimEntity claim = claimService.createClaim(
                request.billId(),
                request.insurerId(),
                facilityId,
                request.totals(),
                request.patientCpid(),
                request.planCode(),
                request.serviceCode(),
                request.preauthRequired()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/ack")
    public ResponseEntity<ApiResponse<ClaimEntity>> receiveAck(
            @PathVariable String id,
            @Valid @RequestBody ClaimAckRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        ClaimEntity claim = claimService.receiveAck(id, request.externalRef());
        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/eligibility-check")
    public ResponseEntity<ApiResponse<ClaimEntity>> checkEligibility(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        ClaimEntity claim = claimService.checkEligibilityGate(id);
        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/preauthorize")
    public ResponseEntity<ApiResponse<ClaimEntity>> preauthorize(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        ClaimEntity claim = claimService.advanceToPreauthorized(id);
        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ClaimEntity>> approve(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        ClaimEntity claim = claimService.approveClaim(id);
        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/remit")
    public ResponseEntity<ApiResponse<ClaimEntity>> remit(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        ClaimEntity claim = claimService.markRemitted(id);
        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<ApiResponse<ClaimEntity>> markPaid(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        ClaimEntity claim = claimService.markPaid(id);
        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/settle")
    public ResponseEntity<ApiResponse<ClaimEntity>> settle(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        ClaimEntity claim = claimService.markSettled(id);
        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/reconcile")
    public ResponseEntity<ApiResponse<ClaimEntity>> reconcile(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();
        ClaimEntity claim = claimService.markReconciled(id);
        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getClaim(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        ClaimEntity claim = claimService.getClaim(id);
        List<ClaimEventEntity> events = claimService.getClaimEvents(id);
        List<ClaimAttachmentEntity> attachments = claimService.getClaimAttachments(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("claim", claim);
        result.put("events", events);
        result.put("attachments", attachments);

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<ClaimEntity>> submitClaim(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        ClaimEntity claim = claimService.submitClaim(id);

        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/adjudication")
    public ResponseEntity<ApiResponse<ClaimEntity>> recordAdjudication(
            @PathVariable String id,
            @Valid @RequestBody ClaimAdjudicationRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        ClaimEntity claim = claimService.recordAdjudication(
                id,
                request.decision(),
                request.patientResidual(),
                request.insurerPayable()
        );

        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<ApiResponse<ClaimEntity>> disputeClaim(
            @PathVariable String id,
            @Valid @RequestBody ClaimDisputeRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        ClaimEntity claim = claimService.disputeClaim(id, request.reason(), ctx.actorId());

        return ResponseEntity.ok(ApiResponse.ok(claim, correlationId));
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<ApiResponse<ClaimAttachmentEntity>> addAttachment(
            @PathVariable String id,
            @Valid @RequestBody ClaimAttachmentRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        ClaimAttachmentEntity attachment = claimService.addAttachment(
                id,
                request.landelaDocId(),
                request.docType()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(attachment, correlationId));
    }
}
