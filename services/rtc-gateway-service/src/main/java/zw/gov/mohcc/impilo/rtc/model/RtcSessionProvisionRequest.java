package zw.gov.mohcc.impilo.rtc.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record RtcSessionProvisionRequest(
        @NotBlank String tenantId,
        @NotBlank String sessionId,
        String referralId,
        String encounterId,
        @NotBlank String patientId,
        String providerId,
        String facilityId,
        String purposeOfUse,
        String consentReference,
        String sessionType,
        String owningService,
        String owningRef,
        @NotNull @Valid RtcParticipant participant,
        Map<String, Object> attributes
) {
}
