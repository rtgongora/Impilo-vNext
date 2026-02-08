package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.*;
import zw.gov.mohcc.impilo.msikaflow.core.PickupTokenService;

import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class PickupController {

    private final PickupTokenService pickupTokenService;

    public PickupController(PickupTokenService pickupTokenService) {
        this.pickupTokenService = pickupTokenService;
    }

    @PostMapping("/orders/{id}/pickup/issue")
    public ResponseEntity<ApiResponse<PickupIssueResponse>> issueToken(
            @PathVariable String id, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        PickupTokenService.PickupIssueResult result = pickupTokenService.issueToken(id, actorId, tenantId);

        String qrPayload = String.format("{\"orderId\":\"%s\",\"token\":\"%s\",\"expiresAt\":\"%s\"}",
                id, result.rawToken(), result.expiresAt().toString());

        PickupIssueResponse response = new PickupIssueResponse(
                result.tokenId(), result.rawToken(), result.rawOtp(), result.expiresAt(), qrPayload);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, correlationId));
    }

    @PostMapping("/pickup/claim")
    public ResponseEntity<ApiResponse<PickupTokenService.ClaimResult>> claimPickup(
            @Valid @RequestBody ClaimPickupRequest req, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String deviceFingerprint = TrustHeaderExtractor.deviceFingerprint(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        PickupTokenService.ClaimResult result = pickupTokenService.claimPickup(
                req.tokenOrOtp(), actorId, actorType, deviceFingerprint, tenantId);

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    @GetMapping("/orders/{id}/pickup/slip.pdf")
    public ResponseEntity<ApiResponse<Object>> getPickupSlip(@PathVariable String id, HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        // In production, generate PDF via document-service/Landela
        // For now, return slip metadata
        return ResponseEntity.ok(ApiResponse.ok(new Object() {
            public final String orderId = id;
            public final String message = "PDF generation via document-service integration";
            public final String downloadUrl = "/v1/orders/" + id + "/pickup/slip.pdf?format=download";
        }, correlationId));
    }
}
