package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimAdjudicationRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimAttachmentRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimCreateRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.ClaimDisputeRequest;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimAttachmentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEventEntity;
import zw.gov.mohcc.impilo.mushex.service.ClaimService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

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

    @PostMapping
    public ResponseEntity<ApiResponse<ClaimEntity>> createClaim(
            @Valid @RequestBody ClaimCreateRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        UUID facilityId = request.facilityId() != null
                ? UUID.fromString(request.facilityId())
                : ctx.facilityId();

        ClaimEntity claim = claimService.createClaim(
                ctx.tenantId(),
                facilityId,
                request.billId(),
                request.insurerId(),
                request.totals()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(claim, correlationId));
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

        ClaimEntity claim = claimService.disputeClaim(id, request.reason());

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
