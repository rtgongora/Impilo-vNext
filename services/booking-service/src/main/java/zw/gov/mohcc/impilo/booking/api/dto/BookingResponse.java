package zw.gov.mohcc.impilo.booking.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingEntity;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        @JsonProperty("booking_number") String bookingNumber,
        @JsonProperty("bookingNumber") String bookingNumberAlias,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("clientId") String clientIdAlias,
        @JsonProperty("booking_type") String bookingType,
        @JsonProperty("bookingType") String bookingTypeAlias,
        @JsonProperty("booking_status") String bookingStatus,
        @JsonProperty("bookingStatus") String bookingStatusAlias,
        @JsonProperty("facility_id") Long facilityId,
        @JsonProperty("facilityId") Long facilityIdAlias,
        @JsonProperty("provider_id") String providerId,
        @JsonProperty("providerId") String providerIdAlias,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("providerName") String providerNameAlias,
        @JsonProperty("linked_appointment_id") UUID linkedAppointmentId,
        @JsonProperty("linkedAppointmentId") UUID linkedAppointmentIdAlias,
        @JsonProperty("triage_status") String triageStatus,
        @JsonProperty("consent_status") String consentStatus,
        @JsonProperty("approval_status") String approvalStatus,
        @JsonProperty("preferred_start_at") Instant preferredStartAt,
        @JsonProperty("preferredStartAt") Instant preferredStartAtAlias,
        String reason,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("createdAt") Instant createdAtAlias
) {
    public static BookingResponse from(BookingEntity entity) {
        return new BookingResponse(
                entity.getId(),
                entity.getBookingNumber(), entity.getBookingNumber(),
                entity.getClientId(), entity.getClientId(),
                entity.getBookingType().name(), entity.getBookingType().name(),
                entity.getBookingStatus().name(), entity.getBookingStatus().name(),
                entity.getFacilityId(), entity.getFacilityId(),
                entity.getProviderId(), entity.getProviderId(),
                entity.getProviderName(), entity.getProviderName(),
                entity.getLinkedAppointmentId(), entity.getLinkedAppointmentId(),
                entity.getTriageStatus().name(),
                entity.getConsentStatus().name(),
                entity.getApprovalStatus().name(),
                entity.getPreferredStartAt(), entity.getPreferredStartAt(),
                entity.getReason(),
                entity.getCreatedAt(), entity.getCreatedAt());
    }
}
