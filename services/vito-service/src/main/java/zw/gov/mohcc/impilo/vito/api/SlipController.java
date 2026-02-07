package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.vito.core.IdentityService;
import zw.gov.mohcc.impilo.vito.core.pdf.PdfGeneratorService;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;

import java.io.IOException;
import java.util.*;

/**
 * PDF slip endpoints — generates patient-facing documents.
 */
@RestController
@RequestMapping("/v1/slips")
public class SlipController {

    private final PdfGeneratorService pdfService;
    private final IdentityService identityService;
    private final HmacService hmacService;

    public SlipController(PdfGeneratorService pdfService, IdentityService identityService,
                           HmacService hmacService) {
        this.pdfService = pdfService;
        this.identityService = identityService;
        this.hmacService = hmacService;
    }

    /**
     * GET /v1/slips/emergency-capsule.pdf — generate Emergency Capsule PDF.
     */
    @GetMapping("/emergency-capsule.pdf")
    public ResponseEntity<byte[]> emergencyCapsule(@RequestParam UUID healthId,
                                                     @RequestParam(defaultValue = "false") boolean hasAllergies,
                                                     @RequestParam(defaultValue = "false") boolean hasMajorConditions,
                                                     @RequestParam(required = false) String payerIndicator) throws IOException {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        ClientEntity client = identityService.findByHealthId(tenantId, healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        String pointer = hmacService.computeLookupHash(healthId.toString());
        String name = (client.getGivenName() != null ? client.getGivenName() : "") + " " +
                       (client.getFamilyName() != null ? client.getFamilyName() : "");

        byte[] pdf = pdfService.generateEmergencyCapsule(
                tenantId.toString(), pointer, name.trim(),
                hasAllergies, hasMajorConditions, payerIndicator, 86400);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=emergency-capsule.pdf")
                .body(pdf);
    }

    /**
     * GET /v1/slips/pickup.pdf — generate Pickup Slip PDF.
     */
    @GetMapping("/pickup.pdf")
    public ResponseEntity<byte[]> pickupSlip(@RequestParam String pickupToken,
                                               @RequestParam String otp,
                                               @RequestParam String delegateName,
                                               @RequestParam String facilityName,
                                               @RequestParam String expiresAt) throws IOException {
        TrustContext ctx = TrustContextHolder.require();
        byte[] pdf = pdfService.generatePickupSlip(
                ctx.tenantId().toString(),
                pickupToken, otp, delegateName, facilityName, expiresAt);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=pickup-slip.pdf")
                .body(pdf);
    }
}
