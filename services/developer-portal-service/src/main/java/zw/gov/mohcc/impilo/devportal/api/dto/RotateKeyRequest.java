package zw.gov.mohcc.impilo.devportal.api.dto;

public record RotateKeyRequest(
        String label,
        Integer expiresInDays
) {}
