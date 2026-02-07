package zw.gov.mohcc.impilo.credential.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

public record IssueCredentialRequest(
        @NotBlank(message = "subjectType is required")
        String subjectType,

        @NotBlank(message = "subjectId is required")
        String subjectId,

        @NotBlank(message = "subjectName is required")
        String subjectName,

        @NotBlank(message = "credentialType is required")
        String credentialType,

        @NotBlank(message = "title is required")
        String title,

        @NotBlank(message = "issuedBy is required")
        String issuedBy,

        @NotNull(message = "validFrom is required")
        LocalDate validFrom,

        LocalDate validTo,

        Map<String, Object> metadata
) {}
