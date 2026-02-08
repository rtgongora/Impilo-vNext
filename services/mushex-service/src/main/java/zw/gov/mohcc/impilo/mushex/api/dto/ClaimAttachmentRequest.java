package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ClaimAttachmentRequest(
        @NotBlank String landelaDocId,
        @NotBlank String docType
) {}
