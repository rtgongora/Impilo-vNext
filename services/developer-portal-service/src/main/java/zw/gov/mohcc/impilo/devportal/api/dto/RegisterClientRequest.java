package zw.gov.mohcc.impilo.devportal.api.dto;

public record RegisterClientRequest(
        String clientName,
        String description,
        String contactEmail,
        boolean sandboxEnabled
) {}
