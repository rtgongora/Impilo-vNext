package zw.gov.mohcc.impilo.tuso.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * BFF-facing appointment shape (camelCase + snake_case aliases for check-in bridges).
 */
public record AppointmentResponse(
        UUID id,
        @JsonProperty("facility_id") Long facilityId,
        @JsonProperty("facilityId") Long facilityIdAlias,
        @JsonProperty("facility_name") String facilityName,
        @JsonProperty("facilityName") String facilityNameAlias,
        @JsonProperty("patient_id") String patientId,
        @JsonProperty("patientId") String patientIdAlias,
        @JsonProperty("patient_cpid") String patientCpid,
        @JsonProperty("patientCpid") String patientCpidAlias,
        @JsonProperty("provider_id") String providerId,
        @JsonProperty("providerId") String providerIdAlias,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("providerName") String providerNameAlias,
        @JsonProperty("appointment_type") String appointmentType,
        @JsonProperty("appointmentType") String appointmentTypeAlias,
        String status,
        @JsonProperty("scheduled_at") Instant scheduledAt,
        @JsonProperty("scheduledAt") Instant scheduledAtAlias,
        @JsonProperty("end_at") Instant endAt,
        @JsonProperty("endAt") Instant endAtAlias,
        String reason,
        String notes,
        @JsonProperty("resource_id") UUID resourceId,
        @JsonProperty("resourceId") UUID resourceIdAlias,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("createdAt") Instant createdAtAlias
) {
    public static AppointmentResponse of(
            UUID id,
            Long facilityId,
            String facilityName,
            String patientId,
            String patientCpid,
            String providerId,
            String providerName,
            String appointmentType,
            String status,
            Instant scheduledAt,
            Instant endAt,
            String reason,
            String notes,
            UUID resourceId,
            Instant createdAt) {
        return new AppointmentResponse(
                id,
                facilityId, facilityId,
                facilityName, facilityName,
                patientId, patientId,
                patientCpid, patientCpid,
                providerId, providerId,
                providerName, providerName,
                appointmentType, appointmentType,
                status,
                scheduledAt, scheduledAt,
                endAt, endAt,
                reason,
                notes,
                resourceId, resourceId,
                createdAt, createdAt);
    }
}
