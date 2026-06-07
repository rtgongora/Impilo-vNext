package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.vito.core.PickupStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "delegated_pickup", schema = "vito")
public class DelegatedPickupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "issuance_request_id", nullable = false)
    private Long issuanceRequestId;

    @Column(name = "pickup_token_hash")
    private String pickupTokenHash;

    @Column(name = "otp_hash")
    private String otpHash;

    @Column(name = "delegate_name")
    private String delegateName;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "delegate_contact", columnDefinition = "jsonb")
    private String delegateContact;

    @Column(name = "delegate_id_ref")
    private String delegateIdRef;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "facility_name")
    private String facilityName;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PickupStatus status = PickupStatus.ACTIVE;

    @Column(name = "redeemed_at")
    private OffsetDateTime redeemedAt;

    @Column(name = "redeemed_by")
    private String redeemedBy;

    @Column(name = "attempts")
    private int attempts = 0;

    @Column(name = "max_attempts")
    private int maxAttempts = 3;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getIssuanceRequestId() { return issuanceRequestId; }
    public void setIssuanceRequestId(Long issuanceRequestId) { this.issuanceRequestId = issuanceRequestId; }
    public String getPickupTokenHash() { return pickupTokenHash; }
    public void setPickupTokenHash(String pickupTokenHash) { this.pickupTokenHash = pickupTokenHash; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public String getDelegateName() { return delegateName; }
    public void setDelegateName(String delegateName) { this.delegateName = delegateName; }
    public String getDelegateContact() { return delegateContact; }
    public void setDelegateContact(String delegateContact) { this.delegateContact = delegateContact; }
    public String getDelegateIdRef() { return delegateIdRef; }
    public void setDelegateIdRef(String delegateIdRef) { this.delegateIdRef = delegateIdRef; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public PickupStatus getStatus() { return status; }
    public void setStatus(PickupStatus status) { this.status = status; }
    public OffsetDateTime getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(OffsetDateTime redeemedAt) { this.redeemedAt = redeemedAt; }
    public String getRedeemedBy() { return redeemedBy; }
    public void setRedeemedBy(String redeemedBy) { this.redeemedBy = redeemedBy; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
