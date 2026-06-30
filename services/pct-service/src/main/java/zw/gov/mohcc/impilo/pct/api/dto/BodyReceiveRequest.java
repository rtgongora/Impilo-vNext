package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Record receipt of a body into mortuary custody (WS#8). */
public record BodyReceiveRequest(
        @NotBlank String bodyTag,
        String storageLocation
) {}
