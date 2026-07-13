package zw.gov.mohcc.impilo.wallet.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cards", schema = "mushe")
public class CardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "card_id", nullable = false, updatable = false, unique = true)
    private UUID cardId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "card_number", nullable = false, unique = true, length = 19)
    private String cardNumber;

    @Column(name = "card_type", nullable = false, length = 16)
    private String cardType = "DUAL";

    @Column(name = "card_network", length = 16)
    private String cardNetwork = "ZIMSWITCH";

    @Column(name = "expiry_month", nullable = false)
    private Integer expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private Integer expiryYear;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ISSUED";

    @Column(name = "pin_hash", length = 255)
    private String pinHash;

    @Column(name = "pin_tries_remaining")
    private Integer pinTriesRemaining = 3;

    @Column(name = "health_applet_version", length = 16)
    private String healthAppletVersion;

    @Column(name = "health_data_updated_at")
    private OffsetDateTime healthDataUpdatedAt;

    @Column(name = "ips_version", length = 16)
    private String ipsVersion;

    @Column(name = "ips_updated_at")
    private OffsetDateTime ipsUpdatedAt;

    /** Reference to the canonical VITO CardCredential (its cardNumber) — the identity facet of this
     * same physical SMART card. Null when the money card is not (yet) linked to a VITO card. */
    @Column(name = "vito_card_number", length = 64)
    private String vitoCardNumber;

    /** The linked VITO card's device/SE P-256 public key (PEM) — verifies offline transactions. */
    @Column(name = "vito_card_public_key", columnDefinition = "TEXT")
    private String vitoCardPublicKey;

    /** Highest accepted offline-transaction counter — enforces monotonicity (replay/gap rejection). */
    @Column(name = "last_offline_counter", nullable = false)
    private long lastOfflineCounter = 0L;

    /**
     * Patient opt-in to the card's PHR-carry function (one of the card's three functions:
     * ID + money + health record). Fail-closed: FALSE means no clinical summary is ever cached
     * on this card. This is a card-FUNCTION activation the cardholder controls — not a record-
     * sharing consent (Mvumo governs sharing PHI with other parties; carrying your own summary
     * on your own card for offline/emergency access is your own choice).
     */
    @Column(name = "phr_enabled", nullable = false)
    private boolean phrEnabled = false;

    /** When the cardholder opted the card into (or out of) carrying their health record. */
    @Column(name = "phr_consent_at")
    private OffsetDateTime phrConsentAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "blocked_at")
    private OffsetDateTime blockedAt;

    @Column(name = "blocked_reason", length = 255)
    private String blockedReason;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (cardId == null) {
            cardId = UUID.randomUUID();
        }
        if (issuedAt == null) {
            issuedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getCardId() {
        return cardId;
    }

    public void setCardId(UUID cardId) {
        this.cardId = cardId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getCardNetwork() {
        return cardNetwork;
    }

    public void setCardNetwork(String cardNetwork) {
        this.cardNetwork = cardNetwork;
    }

    public Integer getExpiryMonth() {
        return expiryMonth;
    }

    public void setExpiryMonth(Integer expiryMonth) {
        this.expiryMonth = expiryMonth;
    }

    public Integer getExpiryYear() {
        return expiryYear;
    }

    public void setExpiryYear(Integer expiryYear) {
        this.expiryYear = expiryYear;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public Integer getPinTriesRemaining() {
        return pinTriesRemaining;
    }

    public void setPinTriesRemaining(Integer pinTriesRemaining) {
        this.pinTriesRemaining = pinTriesRemaining;
    }

    public String getHealthAppletVersion() {
        return healthAppletVersion;
    }

    public void setHealthAppletVersion(String healthAppletVersion) {
        this.healthAppletVersion = healthAppletVersion;
    }

    public OffsetDateTime getHealthDataUpdatedAt() {
        return healthDataUpdatedAt;
    }

    public void setHealthDataUpdatedAt(OffsetDateTime healthDataUpdatedAt) {
        this.healthDataUpdatedAt = healthDataUpdatedAt;
    }

    public String getIpsVersion() {
        return ipsVersion;
    }

    public void setIpsVersion(String ipsVersion) {
        this.ipsVersion = ipsVersion;
    }

    public OffsetDateTime getIpsUpdatedAt() {
        return ipsUpdatedAt;
    }

    public void setIpsUpdatedAt(OffsetDateTime ipsUpdatedAt) {
        this.ipsUpdatedAt = ipsUpdatedAt;
    }

    public String getVitoCardNumber() {
        return vitoCardNumber;
    }

    public void setVitoCardNumber(String vitoCardNumber) {
        this.vitoCardNumber = vitoCardNumber;
    }

    public String getVitoCardPublicKey() {
        return vitoCardPublicKey;
    }

    public void setVitoCardPublicKey(String vitoCardPublicKey) {
        this.vitoCardPublicKey = vitoCardPublicKey;
    }

    public long getLastOfflineCounter() {
        return lastOfflineCounter;
    }

    public void setLastOfflineCounter(long lastOfflineCounter) {
        this.lastOfflineCounter = lastOfflineCounter;
    }

    public boolean isPhrEnabled() {
        return phrEnabled;
    }

    public void setPhrEnabled(boolean phrEnabled) {
        this.phrEnabled = phrEnabled;
    }

    public OffsetDateTime getPhrConsentAt() {
        return phrConsentAt;
    }

    public void setPhrConsentAt(OffsetDateTime phrConsentAt) {
        this.phrConsentAt = phrConsentAt;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(OffsetDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public OffsetDateTime getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(OffsetDateTime blockedAt) {
        this.blockedAt = blockedAt;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(UUID replacedBy) {
        this.replacedBy = replacedBy;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(OffsetDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
