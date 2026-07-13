package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.vito.core.CardStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "smart_card", schema = "vito")
public class SmartCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(name = "health_id", nullable = false)
    private UUID healthId;

    @Column(name = "did_uri", nullable = false)
    private String didUri;

    // G032: absent until the secure-element keypair is provisioned at print time.
    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CardStatus status = CardStatus.REQUESTED;

    /** VIRTUAL (wallet pass on the phone, device-keystore keypair) or PHYSICAL (printed, SE keypair). */
    @Column(name = "card_form", nullable = false)
    private String cardForm = "PHYSICAL";

    @Column(name = "previous_card_id")
    private Long previousCardId;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "printed_at")
    private OffsetDateTime printedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "inactivated_at")
    private OffsetDateTime inactivatedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revocation_reason")
    private String revocationReason;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        requestedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCardForm() { return cardForm; }
    public void setCardForm(String cardForm) { this.cardForm = cardForm; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }
    public String getDidUri() { return didUri; }
    public void setDidUri(String didUri) { this.didUri = didUri; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public CardStatus getStatus() { return status; }
    public void setStatus(CardStatus status) { this.status = status; }
    public Long getPreviousCardId() { return previousCardId; }
    public void setPreviousCardId(Long previousCardId) { this.previousCardId = previousCardId; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getPrintedAt() { return printedAt; }
    public void setPrintedAt(OffsetDateTime printedAt) { this.printedAt = printedAt; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(OffsetDateTime activatedAt) { this.activatedAt = activatedAt; }
    public OffsetDateTime getInactivatedAt() { return inactivatedAt; }
    public void setInactivatedAt(OffsetDateTime inactivatedAt) { this.inactivatedAt = inactivatedAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevocationReason() { return revocationReason; }
    public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
