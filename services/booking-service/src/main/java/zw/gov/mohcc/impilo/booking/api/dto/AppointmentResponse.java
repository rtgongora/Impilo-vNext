package zw.gov.mohcc.impilo.booking.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import zw.gov.mohcc.impilo.booking.persistence.entity.AppointmentEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * BFF-facing appointment shape (camelCase + snake_case aliases for TUSO compatibility).
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
        @JsonProperty("booking_id") UUID bookingId,
        @JsonProperty("bookingId") UUID bookingIdAlias,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("createdAt") Instant createdAtAlias
) {
    public static AppointmentResponse from(AppointmentEntity entity) {
        return new AppointmentResponse(
                entity.getId(),
                entity.getFacilityId(), entity.getFacilityId(),
                entity.getFacilityName(), entity.getFacilityName(),
                entity.getPatientId(), entity.getPatientId(),
                entity.getPatientCpid(), entity.getPatientCpid(),
                entity.getProviderId(), entity.getProviderId(),
                entity.getProviderName(), entity.getProviderName(),
                entity.getAppointmentType(), entity.getAppointmentType(),
                entity.getStatus().name(),
                entity.getScheduledAt(), entity.getScheduledAt(),
                entity.getEndAt(), entity.getEndAt(),
                entity.getReason(),
                entity.getNotes(),
                entity.getResourceId(), entity.getResourceId(),
                entity.getBookingId(), entity.getBookingId(),
                entity.getCreatedAt(), entity.getCreatedAt());
    }
}
