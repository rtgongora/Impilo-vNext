package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;

public record CertificateResponse(
        Long id,
        String documentType,
        String fileName,
        String status,
        Instant uploadedAt
) {}
