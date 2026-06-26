package zw.gov.mohcc.impilo.patientsafety.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * MCAZ request for additional information. The actual outbound message is dispatched by the Comms Hub
 * (channels-service); {@code conversationRef} is the channels session id when one has been created.
 */
public record FollowUpRequestDto(
        @NotBlank String requestText,
        String channelHint,         // COMMS_HUB | EMAIL | PHONE (default COMMS_HUB)
        String conversationRef,     // channels-service session id, if already created
        LocalDate dueDate
) {}
