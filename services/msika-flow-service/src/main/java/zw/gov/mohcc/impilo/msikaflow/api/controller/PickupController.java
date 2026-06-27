package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ClaimPickupRequest;
import zw.gov.mohcc.impilo.msikaflow.api.dto.PickupClaimTrust;
import zw.gov.mohcc.impilo.msikaflow.api.dto.PickupIssueResponse;
import zw.gov.mohcc.impilo.msikaflow.core.PickupSlipPdfService;
import zw.gov.mohcc.impilo.msikaflow.core.PickupTokenService;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class PickupController {

    private static final Logger log = LoggerFactory.getLogger(PickupController.class);

    private final PickupTokenService pickupTokenService;
    private final PickupSlipPdfService pickupSlipPdfService;

    public PickupController(PickupTokenService pickupTokenService,
                            PickupSlipPdfService pickupSlipPdfService) {
        this.pickupTokenService = pickupTokenService;
        this.pickupSlipPdfService = pickupSlipPdfService;
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
        Integer assurance = TrustHeaderExtractor.assuranceLevel(httpReq);

        PickupClaimTrust trust =
                new PickupClaimTrust(tenantId, correlationId, actorId, actorType, assurance, deviceFingerprint);
        PickupTokenService.ClaimResult result = pickupTokenService.claimPickup(req.tokenOrOtp(), trust);

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }

    /**
     * Streams a real printable pickup-slip PDF for the order's active pickup token (G034 —
     * was a JSON stub). Pass the client-held {@code token} (from the issue response) to embed a
     * scannable QR; it is validated against the stored hash and never persisted. Returns 404 when
     * the order has no active token, 400 when a supplied token does not match.
     */
    @GetMapping(value = "/orders/{id}/pickup/slip.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getPickupSlip(
            @PathVariable String id,
            @RequestParam(name = "token", required = false) String token,
            HttpServletRequest httpReq) {
        try {
            PickupTokenService.PickupSlipContext ctx = pickupTokenService.resolveSlipContext(id, token);
            byte[] pdf = pickupSlipPdfService.generatePickupSlipPdf(
                    id, ctx.tokenId(), ctx.expiresAt(), ctx.status(),
                    ctx.tokenVerified() ? token : null);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"pickup-slip-" + id + ".pdf\"")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("No active")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            log.warn("Pickup slip render rejected for order {}: {}", id, e.getMessage());
            return ResponseEntity.status(status).build();
        } catch (IOException e) {
            log.error("Pickup slip PDF generation failed for order {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
