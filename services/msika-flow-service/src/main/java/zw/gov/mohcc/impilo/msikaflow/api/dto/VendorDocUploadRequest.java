package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VendorDocUploadRequest(
        @NotBlank String docType,
        @NotBlank String landelaDocId
) {}
