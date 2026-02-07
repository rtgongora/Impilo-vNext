package zw.gov.mohcc.impilo.varapi.api.dto;

public record StatusChangeRequest(
        String newStatus,
        String reason
) {}
