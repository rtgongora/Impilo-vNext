package zw.gov.mohcc.impilo.support.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EscalateTicketRequest(
        @NotBlank String escalatedBy,
        @NotNull Integer targetLevel) {}
