package zw.gov.mohcc.impilo.booking.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.booking.domain.AppointmentStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "appointment", schema = "booking")
public class AppointmentEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "appointment_number", nullable = false, length = 32)
    private String appointmentNumber;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "patient_cpid", nullable = false)
    private String patientCpid;

    @Column(name = "patient_id")
    private String patientId;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "facility_id")
    private Long facilityId;

    @Column(name = "facility_name")
    private String facilityName;

    @Column(name = "service_id")
    private String serviceId;

    @Column(name = "appointment_type", nullable = false, length = 50)
    private String appointmentType;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "timezone", length = 64)
    private String timezone = "Africa/Harare";

    @Column(name = "channel", length = 32)
    private String channel;

    @Column(name = "location", columnDefinition = "TEXT")
    private String location;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "virtual_meeting_details", columnDefinition = "jsonb")
    private Map<String, Object> virtualMeetingDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(name = "check_in_status", nullable = false, length = 32)
    private String checkInStatus = "NOT_CHECKED_IN";

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "queue_token_id")
    private UUID queueTokenId;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (scheduledAt == null) {
            scheduledAt = startTime;
        }
        if (endAt == null) {
            endAt = endTime;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        scheduledAt = startTime;
        endAt = endTime;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public String getAppointmentNumber() { return appointmentNumber; }
    public void setAppointmentNumber(String appointmentNumber) { this.appointmentNumber = appointmentNumber; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public String getAppointmentType() { return appointmentType; }
    public void setAppointmentType(String appointmentType) { this.appointmentType = appointmentType; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Map<String, Object> getVirtualMeetingDetails() { return virtualMeetingDetails; }
    public void setVirtualMeetingDetails(Map<String, Object> virtualMeetingDetails) { this.virtualMeetingDetails = virtualMeetingDetails; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public String getCheckInStatus() { return checkInStatus; }
    public void setCheckInStatus(String checkInStatus) { this.checkInStatus = checkInStatus; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public UUID getQueueTokenId() { return queueTokenId; }
    public void setQueueTokenId(UUID queueTokenId) { this.queueTokenId = queueTokenId; }
    public UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
