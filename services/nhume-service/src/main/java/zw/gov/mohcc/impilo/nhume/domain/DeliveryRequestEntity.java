package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Nhume delivery request — the canonical movement aggregate.
 */
@Entity
@Table(name = "nhume_delivery_requests")
public class DeliveryRequestEntity {

    @Id
    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "reference_code", nullable = false, length = 32)
    private String referenceCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status = DeliveryStatus.DRAFT.name();

    @Column(name = "priority", nullable = false, length = 16)
    private String priority = DeliveryPriority.STANDARD.name();

    @Column(name = "delivery_type", nullable = false, length = 48)
    private String deliveryType = DeliveryType.GENERAL.name();

    @Column(name = "request_source", nullable = false, length = 48)
    private String requestSource;

    @Column(name = "requesting_actor_id", length = 255)
    private String requestingActorId;

    @Column(name = "requesting_actor_type", length = 48)
    private String requestingActorType;

    @Column(name = "requesting_org_id", length = 255)
    private String requestingOrgId;

    @Column(name = "requesting_facility_id", length = 255)
    private String requestingFacilityId;

    @Column(name = "origin_kind", nullable = false, length = 32)
    private String originKind;

    @Column(name = "origin_ref", nullable = false, length = 255)
    private String originRef;

    @Column(name = "origin_label", length = 255)
    private String originLabel;

    @Column(name = "origin_address_line", length = 512)
    private String originAddressLine;

    @Column(name = "origin_lat")
    private Double originLat;

    @Column(name = "origin_lng")
    private Double originLng;

    @Column(name = "destination_kind", nullable = false, length = 32)
    private String destinationKind;

    @Column(name = "destination_ref", nullable = false, length = 255)
    private String destinationRef;

    @Column(name = "destination_label", length = 255)
    private String destinationLabel;

    @Column(name = "destination_address_line", length = 512)
    private String destinationAddressLine;

    @Column(name = "destination_lat")
    private Double destinationLat;

    @Column(name = "destination_lng")
    private Double destinationLng;

    @Column(name = "recipient_kind", length = 32)
    private String recipientKind;

    @Column(name = "recipient_ref", length = 255)
    private String recipientRef;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "recipient_phone", length = 64)
    private String recipientPhone;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "recipient_language", length = 16)
    private String recipientLanguage;

    @Column(name = "clinical_context_ref", length = 255)
    private String clinicalContextRef;

    @Column(name = "programme_context_ref", length = 255)
    private String programmeContextRef;

    @Column(name = "telehealth_session_ref", length = 255)
    private String telehealthSessionRef;

    @Column(name = "marketplace_order_ref", length = 255)
    private String marketplaceOrderRef;

    @Column(name = "payment_path", nullable = false, length = 48)
    private String paymentPath = "NONE";

    @Column(name = "payment_reference", length = 255)
    private String paymentReference;

    @Column(name = "declared_value_cents")
    private Long declaredValueCents;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "consent_required", nullable = false)
    private boolean consentRequired;

    @Column(name = "identity_verification", nullable = false, length = 32)
    private String identityVerification = "NONE";

    @Column(name = "cold_chain_required", nullable = false)
    private boolean coldChainRequired;

    @Column(name = "controlled_item", nullable = false)
    private boolean controlledItem;

    @Column(name = "fragile", nullable = false)
    private boolean fragile;

    @Column(name = "hazardous", nullable = false)
    private boolean hazardous;

    @Column(name = "biohazard", nullable = false)
    private boolean biohazard;

    @Column(name = "specimen", nullable = false)
    private boolean specimen;

    @Column(name = "chain_of_custody_required", nullable = false)
    private boolean chainOfCustodyRequired;

    @Column(name = "return_required", nullable = false)
    private boolean returnRequired;

    /** OF-B18 §12.4 — explicit required proof-of-handover grade; NULL = legacy ungraded class. */
    @Column(name = "required_pod_grade", length = 32)
    private String requiredPodGrade;

    /** OF-B18 — the handover must be to the named recipient; verification failure fails closed. */
    @Column(name = "named_recipient_required", nullable = false)
    private boolean namedRecipientRequired;

    /** Server-side OTP truth for the OTP_MATCH grade — never sent to the courier surface. */
    @Column(name = "handover_otp", length = 12)
    private String handoverOtp;

    /** OF-B18 §12.7 — bounded reattempt counter (attempts exhausted ⇒ RETURNED). */
    @Column(name = "handover_attempts", nullable = false)
    private int handoverAttempts;

    @Column(name = "max_handover_attempts", nullable = false)
    private int maxHandoverAttempts = 3;

    @Column(name = "required_by")
    private OffsetDateTime requiredBy;

    @Column(name = "pickup_window_start")
    private OffsetDateTime pickupWindowStart;

    @Column(name = "pickup_window_end")
    private OffsetDateTime pickupWindowEnd;

    @Column(name = "delivery_window_start")
    private OffsetDateTime deliveryWindowStart;

    @Column(name = "delivery_window_end")
    private OffsetDateTime deliveryWindowEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_modes_json", nullable = false, columnDefinition = "jsonb")
    private String allowedModesJson = "[]";

    @Column(name = "policy_id", length = 64)
    private String policyId;

    @Column(name = "sla_id", length = 64)
    private String slaId;

    @Column(name = "sla_due_at")
    private OffsetDateTime slaDueAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    protected DeliveryRequestEntity() {}

    public DeliveryRequestEntity(UUID deliveryId, UUID tenantId, String referenceCode,
                                  String requestSource) {
        this.deliveryId = deliveryId;
        this.tenantId = tenantId;
        this.referenceCode = referenceCode;
        this.requestSource = requestSource;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getDeliveryId() { return deliveryId; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getDeliveryType() { return deliveryType; }
    public void setDeliveryType(String deliveryType) { this.deliveryType = deliveryType; }
    public String getRequestSource() { return requestSource; }
    public void setRequestSource(String requestSource) { this.requestSource = requestSource; }
    public String getRequestingActorId() { return requestingActorId; }
    public void setRequestingActorId(String v) { this.requestingActorId = v; }
    public String getRequestingActorType() { return requestingActorType; }
    public void setRequestingActorType(String v) { this.requestingActorType = v; }
    public String getRequestingOrgId() { return requestingOrgId; }
    public void setRequestingOrgId(String v) { this.requestingOrgId = v; }
    public String getRequestingFacilityId() { return requestingFacilityId; }
    public void setRequestingFacilityId(String v) { this.requestingFacilityId = v; }
    public String getOriginKind() { return originKind; }
    public void setOriginKind(String v) { this.originKind = v; }
    public String getOriginRef() { return originRef; }
    public void setOriginRef(String v) { this.originRef = v; }
    public String getOriginLabel() { return originLabel; }
    public void setOriginLabel(String v) { this.originLabel = v; }
    public String getOriginAddressLine() { return originAddressLine; }
    public void setOriginAddressLine(String v) { this.originAddressLine = v; }
    public Double getOriginLat() { return originLat; }
    public void setOriginLat(Double v) { this.originLat = v; }
    public Double getOriginLng() { return originLng; }
    public void setOriginLng(Double v) { this.originLng = v; }
    public String getDestinationKind() { return destinationKind; }
    public void setDestinationKind(String v) { this.destinationKind = v; }
    public String getDestinationRef() { return destinationRef; }
    public void setDestinationRef(String v) { this.destinationRef = v; }
    public String getDestinationLabel() { return destinationLabel; }
    public void setDestinationLabel(String v) { this.destinationLabel = v; }
    public String getDestinationAddressLine() { return destinationAddressLine; }
    public void setDestinationAddressLine(String v) { this.destinationAddressLine = v; }
    public Double getDestinationLat() { return destinationLat; }
    public void setDestinationLat(Double v) { this.destinationLat = v; }
    public Double getDestinationLng() { return destinationLng; }
    public void setDestinationLng(Double v) { this.destinationLng = v; }
    public String getRecipientKind() { return recipientKind; }
    public void setRecipientKind(String v) { this.recipientKind = v; }
    public String getRecipientRef() { return recipientRef; }
    public void setRecipientRef(String v) { this.recipientRef = v; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String v) { this.recipientName = v; }
    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String v) { this.recipientPhone = v; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String v) { this.recipientEmail = v; }
    public String getRecipientLanguage() { return recipientLanguage; }
    public void setRecipientLanguage(String v) { this.recipientLanguage = v; }
    public String getClinicalContextRef() { return clinicalContextRef; }
    public void setClinicalContextRef(String v) { this.clinicalContextRef = v; }
    public String getProgrammeContextRef() { return programmeContextRef; }
    public void setProgrammeContextRef(String v) { this.programmeContextRef = v; }
    public String getTelehealthSessionRef() { return telehealthSessionRef; }
    public void setTelehealthSessionRef(String v) { this.telehealthSessionRef = v; }
    public String getMarketplaceOrderRef() { return marketplaceOrderRef; }
    public void setMarketplaceOrderRef(String v) { this.marketplaceOrderRef = v; }
    public String getPaymentPath() { return paymentPath; }
    public void setPaymentPath(String v) { this.paymentPath = v; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String v) { this.paymentReference = v; }
    public Long getDeclaredValueCents() { return declaredValueCents; }
    public void setDeclaredValueCents(Long v) { this.declaredValueCents = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public boolean isConsentRequired() { return consentRequired; }
    public void setConsentRequired(boolean v) { this.consentRequired = v; }
    public String getIdentityVerification() { return identityVerification; }
    public void setIdentityVerification(String v) { this.identityVerification = v; }
    public boolean isColdChainRequired() { return coldChainRequired; }
    public void setColdChainRequired(boolean v) { this.coldChainRequired = v; }
    public boolean isControlledItem() { return controlledItem; }
    public void setControlledItem(boolean v) { this.controlledItem = v; }
    public boolean isFragile() { return fragile; }
    public void setFragile(boolean v) { this.fragile = v; }
    public boolean isHazardous() { return hazardous; }
    public void setHazardous(boolean v) { this.hazardous = v; }
    public boolean isBiohazard() { return biohazard; }
    public void setBiohazard(boolean v) { this.biohazard = v; }
    public boolean isSpecimen() { return specimen; }
    public void setSpecimen(boolean v) { this.specimen = v; }
    public boolean isChainOfCustodyRequired() { return chainOfCustodyRequired; }
    public void setChainOfCustodyRequired(boolean v) { this.chainOfCustodyRequired = v; }
    public boolean isReturnRequired() { return returnRequired; }
    public void setReturnRequired(boolean v) { this.returnRequired = v; }
    public String getRequiredPodGrade() { return requiredPodGrade; }
    public void setRequiredPodGrade(String v) { this.requiredPodGrade = v; }
    public boolean isNamedRecipientRequired() { return namedRecipientRequired; }
    public void setNamedRecipientRequired(boolean v) { this.namedRecipientRequired = v; }
    public String getHandoverOtp() { return handoverOtp; }
    public void setHandoverOtp(String v) { this.handoverOtp = v; }
    public int getHandoverAttempts() { return handoverAttempts; }
    public void setHandoverAttempts(int v) { this.handoverAttempts = v; }
    public int getMaxHandoverAttempts() { return maxHandoverAttempts; }
    public void setMaxHandoverAttempts(int v) { this.maxHandoverAttempts = v; }
    public OffsetDateTime getRequiredBy() { return requiredBy; }
    public void setRequiredBy(OffsetDateTime v) { this.requiredBy = v; }
    public OffsetDateTime getPickupWindowStart() { return pickupWindowStart; }
    public void setPickupWindowStart(OffsetDateTime v) { this.pickupWindowStart = v; }
    public OffsetDateTime getPickupWindowEnd() { return pickupWindowEnd; }
    public void setPickupWindowEnd(OffsetDateTime v) { this.pickupWindowEnd = v; }
    public OffsetDateTime getDeliveryWindowStart() { return deliveryWindowStart; }
    public void setDeliveryWindowStart(OffsetDateTime v) { this.deliveryWindowStart = v; }
    public OffsetDateTime getDeliveryWindowEnd() { return deliveryWindowEnd; }
    public void setDeliveryWindowEnd(OffsetDateTime v) { this.deliveryWindowEnd = v; }
    public String getAllowedModesJson() { return allowedModesJson; }
    public void setAllowedModesJson(String v) { this.allowedModesJson = v; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String v) { this.policyId = v; }
    public String getSlaId() { return slaId; }
    public void setSlaId(String v) { this.slaId = v; }
    public OffsetDateTime getSlaDueAt() { return slaDueAt; }
    public void setSlaDueAt(OffsetDateTime v) { this.slaDueAt = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { this.metadataJson = v; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID v) { this.correlationId = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime v) { this.submittedAt = v; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime v) { this.approvedAt = v; }
    public OffsetDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(OffsetDateTime v) { this.rejectedAt = v; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(OffsetDateTime v) { this.cancelledAt = v; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(OffsetDateTime v) { this.deliveredAt = v; }
    public OffsetDateTime getFailedAt() { return failedAt; }
    public void setFailedAt(OffsetDateTime v) { this.failedAt = v; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime v) { this.closedAt = v; }
}
