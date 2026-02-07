package zw.gov.mohcc.impilo.credential.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record VerificationResult(
        String status,
        String credentialType,
        String subjectName,
        String title,
        String issuedBy,
        LocalDate validFrom,
        LocalDate validTo,
        Instant verifiedAt
) {}
