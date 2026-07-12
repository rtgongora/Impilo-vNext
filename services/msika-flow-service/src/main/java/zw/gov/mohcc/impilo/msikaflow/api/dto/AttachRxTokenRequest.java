package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /v1/rx/attach-token — verify + attach a share-slip Rx token. */
public record AttachRxTokenRequest(
        @NotBlank String orderId,
        @NotBlank String token
) {}
