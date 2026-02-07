package zw.gov.mohcc.impilo.credential.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CredentialResponse(
        UUID credentialId,
        UUID tenantId,
        String subjectType,
        String subjectId,
        String subjectName,
        String credentialType,
        String title,
        String issuedBy,
        Instant issuedAt,
        LocalDate validFrom,
        LocalDate validTo,
        String status,
        String verificationUrl,
        UUID pdfDocumentId,
        Map<String, Object> metadata,
        Instant createdAt
) {}
