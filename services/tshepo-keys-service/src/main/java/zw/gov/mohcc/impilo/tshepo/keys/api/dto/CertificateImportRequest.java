package zw.gov.mohcc.impilo.tshepo.keys.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to import a CA certificate into the trust store.
 */
public record CertificateImportRequest(
        @NotNull UUID tenantId,
        @NotBlank String certificatePem
) {}
