package zw.gov.mohcc.impilo.channels.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
        @NotBlank String channelType,
        String subjectRef,
        String facilityId
) {}
