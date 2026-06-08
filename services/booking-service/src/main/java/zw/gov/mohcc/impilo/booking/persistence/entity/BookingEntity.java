package zw.gov.mohcc.impilo.booking.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.booking.domain.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking", schema = "booking")
public class BookingEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "booking_number", nullable = false, length = 32)
    private String bookingNumber;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "requested_by_actor_id", nullable = false)
    private String requestedByActorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by_actor_type", nullable = false, length = 32)
    private ActorType requestedByActorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false, length = 50)
    private BookingType bookingType;

    @Column(name = "service_id")
    private String serviceId;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "facility_id")
    private Long facilityId;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "provider_name")
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 32)
    private TargetType targetType;

    @Column(name = "target_ref")
    private String targetRef;

    @Column(name = "preferred_start_at")
    private Instant preferredStartAt;

    @Column(name = "preferred_end_at")
    private Instant preferredEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", length = 32)
    private PreferredChannel preferredChannel;

    @Column(name = "location", columnDefinition = "TEXT")
    private String location;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "clinical_priority", nullable = false, length = 32)
    private ClinicalPriority clinicalPriority = ClinicalPriority.ROUTINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "triage_status", nullable = false, length = 32)
    private TriageStatus triageStatus = TriageStatus.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_status", nullable = false, length = 32)
    private EligibilityStatus eligibilityStatus = EligibilityStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_status", nullable = false, length = 32)
    private ConsentStatus consentStatus = ConsentStatus.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_status", nullable = false, length = 32)
    private AgreementStatus agreementStatus = AgreementStatus.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorisation_status", nullable = false, length = 32)
    private AuthorisationStatus authorisationStatus = AuthorisationStatus.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 32)
    private ApprovalStatus approvalStatus = ApprovalStatus.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 32)
    private BookingStatus bookingStatus = BookingStatus.REQUESTED;

    @Column(name = "linked_appointment_id")
    private UUID linkedAppointmentId;

    @Column(name = "specialty")
    private String specialty;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "tuso_booking_id")
    private UUID tusoBookingId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getBookingNumber() { return bookingNumber; }
    public void setBookingNumber(String bookingNumber) { this.bookingNumber = bookingNumber; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getRequestedByActorId() { return requestedByActorId; }
    public void setRequestedByActorId(String requestedByActorId) { this.requestedByActorId = requestedByActorId; }
    public ActorType getRequestedByActorType() { return requestedByActorType; }
    public void setRequestedByActorType(ActorType requestedByActorType) { this.requestedByActorType = requestedByActorType; }
    public BookingType getBookingType() { return bookingType; }
    public void setBookingType(BookingType bookingType) { this.bookingType = bookingType; }
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public TargetType getTargetType() { return targetType; }
    public void setTargetType(TargetType targetType) { this.targetType = targetType; }
    public String getTargetRef() { return targetRef; }
    public void setTargetRef(String targetRef) { this.targetRef = targetRef; }
    public Instant getPreferredStartAt() { return preferredStartAt; }
    public void setPreferredStartAt(Instant preferredStartAt) { this.preferredStartAt = preferredStartAt; }
    public Instant getPreferredEndAt() { return preferredEndAt; }
    public void setPreferredEndAt(Instant preferredEndAt) { this.preferredEndAt = preferredEndAt; }
    public PreferredChannel getPreferredChannel() { return preferredChannel; }
    public void setPreferredChannel(PreferredChannel preferredChannel) { this.preferredChannel = preferredChannel; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public ClinicalPriority getClinicalPriority() { return clinicalPriority; }
    public void setClinicalPriority(ClinicalPriority clinicalPriority) { this.clinicalPriority = clinicalPriority; }
    public TriageStatus getTriageStatus() { return triageStatus; }
    public void setTriageStatus(TriageStatus triageStatus) { this.triageStatus = triageStatus; }
    public EligibilityStatus getEligibilityStatus() { return eligibilityStatus; }
    public void setEligibilityStatus(EligibilityStatus eligibilityStatus) { this.eligibilityStatus = eligibilityStatus; }
    public ConsentStatus getConsentStatus() { return consentStatus; }
    public void setConsentStatus(ConsentStatus consentStatus) { this.consentStatus = consentStatus; }
    public AgreementStatus getAgreementStatus() { return agreementStatus; }
    public void setAgreementStatus(AgreementStatus agreementStatus) { this.agreementStatus = agreementStatus; }
    public AuthorisationStatus getAuthorisationStatus() { return authorisationStatus; }
    public void setAuthorisationStatus(AuthorisationStatus authorisationStatus) { this.authorisationStatus = authorisationStatus; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(ApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }
    public BookingStatus getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(BookingStatus bookingStatus) { this.bookingStatus = bookingStatus; }
    public UUID getLinkedAppointmentId() { return linkedAppointmentId; }
    public void setLinkedAppointmentId(UUID linkedAppointmentId) { this.linkedAppointmentId = linkedAppointmentId; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
    public UUID getTusoBookingId() { return tusoBookingId; }
    public void setTusoBookingId(UUID tusoBookingId) { this.tusoBookingId = tusoBookingId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
