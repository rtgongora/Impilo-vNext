package zw.gov.mohcc.impilo.tshepo.keys.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public-facing representation of a trusted CA certificate.
 */
public record CertificateResponse(
        Long id,
        UUID tenantId,
        String subjectDn,
        String issuerDn,
        String serialNumber,
        String fingerprintSha256,
        String status,
        Instant importedAt,
        Instant expiresAt
) {}
