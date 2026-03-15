package zw.gov.mohcc.impilo.support.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignTicketRequest(
        @NotBlank String assigneeRef,
        @NotBlank String assignedBy) {}
