package zw.gov.mohcc.impilo.vito.api.dto;

import java.util.UUID;

public record ExternalClientRegistrationResponse(
        UUID healthId,
        String impiloId,
        String status,
        String syncStatus,
        String correlationId
) {}
