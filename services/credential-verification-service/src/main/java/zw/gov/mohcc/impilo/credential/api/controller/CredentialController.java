package zw.gov.mohcc.impilo.credential.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.credential.api.dto.*;
import zw.gov.mohcc.impilo.credential.core.CredentialService;
import zw.gov.mohcc.impilo.credential.core.PdfGeneratorService;
import zw.gov.mohcc.impilo.credential.persistence.entity.CredentialEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.UUID;

/**
 * Authenticated credential management endpoints.
 *
 * All endpoints require a valid JWT and trust headers.
 */
@RestController
@RequestMapping("/v1/internal/credentials")
public class CredentialController {

    private final CredentialService credentialService;
    private final PdfGeneratorService pdfGeneratorService;

    public CredentialController(CredentialService credentialService,
                                PdfGeneratorService pdfGeneratorService) {
        this.credentialService = credentialService;
        this.pdfGeneratorService = pdfGeneratorService;
    }

    /**
     * Issue a new digitally signed credential.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CredentialResponse>> issueCredential(
            @Valid @RequestBody IssueCredentialRequest request) {
        String correlationId = getCorrelationId();
        CredentialResponse response = credentialService.issueCredential(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(response, correlationId));
    }

    /**
     * Get a credential by its UUID.
     */
    @GetMapping("/{credentialId}")
    public ResponseEntity<ApiResponse<CredentialResponse>> getCredential(
            @PathVariable UUID credentialId) {
        String correlationId = getCorrelationId();
        CredentialResponse response = credentialService.getCredential(credentialId);
        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * Search credentials with optional filters.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CredentialResponse>>> searchCredentials(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String credentialType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String correlationId = getCorrelationId();

        CredentialSearchRequest search = new CredentialSearchRequest(
                subjectType, subjectId, credentialType, status, page, size);

        PagedResponse<CredentialResponse> response = credentialService.searchCredentials(search);
        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * Revoke a credential.
     */
    @PostMapping("/{credentialId}/revoke")
    public ResponseEntity<ApiResponse<CredentialResponse>> revokeCredential(
            @PathVariable UUID credentialId,
            @Valid @RequestBody RevokeCredentialRequest request) {
        String correlationId = getCorrelationId();
        CredentialResponse response = credentialService.revokeCredential(credentialId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * Supersede a credential with a new one.
     */
    @PostMapping("/{credentialId}/supersede")
    public ResponseEntity<ApiResponse<CredentialResponse>> supersedeCredential(
            @PathVariable UUID credentialId,
            @Valid @RequestBody SupersedeCredentialRequest request) {
        String correlationId = getCorrelationId();
        CredentialResponse response = credentialService.supersedeCredential(
                credentialId, request.newCredentialId());
        return ResponseEntity.ok(ApiResponse.ok(response, correlationId));
    }

    /**
     * Generate and return a PDF certificate for a credential.
     */
    @GetMapping("/{credentialId}/pdf")
    public ResponseEntity<byte[]> generatePdf(@PathVariable UUID credentialId) {
        CredentialEntity entity = credentialService.getCredentialEntity(credentialId);
        byte[] pdfBytes = pdfGeneratorService.generateCertificatePdf(entity);

        String filename = "credential-" + entity.getCredentialId().toString() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }

    private String getCorrelationId() {
        var ctx = TrustContextHolder.get();
        return ctx != null && ctx.correlationId() != null
                ? ctx.correlationId().toString()
                : UUID.randomUUID().toString();
    }
}
