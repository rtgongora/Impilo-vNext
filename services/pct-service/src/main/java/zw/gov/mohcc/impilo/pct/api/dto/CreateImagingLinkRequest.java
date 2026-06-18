package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateImagingLinkRequest(
        @NotBlank String governedStudyId,
        String orosOrderId,
        String linkType,
        UUID encounterRef
) {
}
