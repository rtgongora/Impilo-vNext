package zw.gov.mohcc.impilo.tshepo.keys.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.keys.api.dto.CertificateImportRequest;
import zw.gov.mohcc.impilo.tshepo.keys.api.dto.CertificateResponse;
import zw.gov.mohcc.impilo.tshepo.keys.core.CertificateTrustService;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.CertificateTrustEntity;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only certificate trust management endpoints.
 *
 * <p>Manages the CA certificate trust store used for mTLS verification.
 * Supports importing, listing, and revoking trusted certificates.</p>
 */
@RestController
@RequestMapping("/v1/certificates")
public class CertificateController {

    private final CertificateTrustService certificateTrustService;

    public CertificateController(CertificateTrustService certificateTrustService) {
        this.certificateTrustService = certificateTrustService;
    }

    /**
     * POST /v1/certificates — Import a CA certificate into the trust store.
     */
    @PostMapping
    public ResponseEntity<CertificateResponse> importCertificate(@Valid @RequestBody CertificateImportRequest request) {
        CertificateTrustEntity entity = certificateTrustService.importCertificate(
                request.tenantId(), request.certificatePem());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    /**
     * GET /v1/certificates — List all certificates for a tenant.
     */
    @GetMapping
    public ResponseEntity<List<CertificateResponse>> listCertificates(@RequestParam UUID tenantId) {
        List<CertificateResponse> certs = certificateTrustService.listCertificates(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(certs);
    }

    /**
     * DELETE /v1/certificates/{id} — Revoke a trusted certificate.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeCertificate(@PathVariable Long id, @RequestParam UUID tenantId) {
        certificateTrustService.revokeCertificate(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    private CertificateResponse toResponse(CertificateTrustEntity entity) {
        return new CertificateResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getSubjectDn(),
                entity.getIssuerDn(),
                entity.getSerialNumber(),
                entity.getFingerprintSha256(),
                entity.getStatus(),
                entity.getImportedAt(),
                entity.getExpiresAt()
        );
    }
}
