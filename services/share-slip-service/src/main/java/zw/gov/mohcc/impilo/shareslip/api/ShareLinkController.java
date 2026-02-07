package zw.gov.mohcc.impilo.shareslip.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.shareslip.core.ShareLinkService;
import zw.gov.mohcc.impilo.shareslip.core.ShareSlipPdfService;
import zw.gov.mohcc.impilo.shareslip.dto.CreateShareLinkRequest;
import zw.gov.mohcc.impilo.shareslip.dto.ShareLinkResponse;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareAuditEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareLinkEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.ShareAuditRepository;

import java.io.IOException;
import java.util.UUID;

/**
 * Authenticated controller for share link management.
 *
 * All endpoints require a valid OAuth2 token (internal services via Envoy).
 * Provides CRUD operations, PDF generation, and audit trail access.
 *
 * Endpoints:
 *   POST   /v1/internal/share-links           -- create a new share link
 *   GET    /v1/internal/share-links/{linkId}   -- get share link details
 *   GET    /v1/internal/share-links            -- list by subject
 *   POST   /v1/internal/share-links/{linkId}/revoke  -- revoke a share link
 *   GET    /v1/internal/share-links/{linkId}/pdf     -- generate PDF slip
 *   GET    /v1/internal/share-links/{linkId}/audit   -- audit trail
 */
@RestController
@RequestMapping("/v1/internal/share-links")
public class ShareLinkController {

    private final ShareLinkService shareLinkService;
    private final ShareSlipPdfService shareSlipPdfService;
    private final ShareAuditRepository shareAuditRepository;

    public ShareLinkController(ShareLinkService shareLinkService,
                               ShareSlipPdfService shareSlipPdfService,
                               ShareAuditRepository shareAuditRepository) {
        this.shareLinkService = shareLinkService;
        this.shareSlipPdfService = shareSlipPdfService;
        this.shareAuditRepository = shareAuditRepository;
    }

    /**
     * Create a new share link with OTP protection.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ShareLinkResponse>> createShareLink(
            @Valid @RequestBody CreateShareLinkRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        ShareLinkResponse response = shareLinkService.createShareLink(request);
        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    /**
     * Get a share link by its UUID.
     */
    @GetMapping("/{linkId}")
    public ResponseEntity<ApiResponse<ShareLinkResponse>> getShareLink(@PathVariable UUID linkId) {
        TrustContext ctx = TrustContextHolder.require();
        ShareLinkResponse response = shareLinkService.getShareLink(linkId);
        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    /**
     * List share links by subject type and subject ID.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ShareLinkResponse>>> listShareLinks(
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<ShareLinkResponse> response = shareLinkService.listShareLinks(subjectType, subjectId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    /**
     * Revoke a share link, preventing further claims.
     */
    @PostMapping("/{linkId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeShareLink(@PathVariable UUID linkId) {
        TrustContext ctx = TrustContextHolder.require();
        shareLinkService.revokeShareLink(linkId);
        return ResponseEntity.ok(ApiResponse.ok(null, ctx.correlationId().toString()));
    }

    /**
     * Generate a share slip PDF for printing/downloading.
     */
    @GetMapping(value = "/{linkId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generatePdf(@PathVariable UUID linkId) throws IOException {
        ShareLinkEntity entity = shareLinkService.getShareLinkEntity(linkId);
        byte[] pdf = shareSlipPdfService.generateShareSlipPdf(entity);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                "share-slip-" + entity.getLinkId() + ".pdf");
        headers.setContentLength(pdf.length);

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    /**
     * Get the audit trail for a share link.
     */
    @GetMapping("/{linkId}/audit")
    public ResponseEntity<ApiResponse<PagedResponse<ShareAuditEntity>>> getAuditTrail(
            @PathVariable UUID linkId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Pageable pageable = PageRequest.of(page, size);
        Page<ShareAuditEntity> auditPage = shareAuditRepository.findByLinkIdOrderByCreatedAtDesc(linkId, pageable);

        PagedResponse<ShareAuditEntity> response = PagedResponse.of(
                auditPage.getContent(),
                auditPage.getNumber(),
                auditPage.getSize(),
                auditPage.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }
}
