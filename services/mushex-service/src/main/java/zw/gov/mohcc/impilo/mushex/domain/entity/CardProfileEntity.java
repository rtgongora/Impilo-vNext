package zw.gov.mohcc.impilo.mushex.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mushex_card_profiles")
public class CardProfileEntity {

    @Id
    @Column(name = "card_profile_id", length = 26)
    private String cardProfileId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "wallet_id", length = 26)
    private String walletId;

    @Column(name = "owner_ref", length = 128)
    private String ownerRef;

    @Column(name = "card_type", nullable = false, length = 40)
    private String cardType;

    @Column(name = "card_status", nullable = false, length = 30)
    private String cardStatus = "ISSUED";

    @Column(name = "token_ref", length = 200)
    private String tokenRef;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expiry_meta", columnDefinition = "jsonb")
    private String expiryMeta;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (issuedAt == null) {
            issuedAt = OffsetDateTime.now();
        }
    }

    public String getCardProfileId() { return cardProfileId; }
    public void setCardProfileId(String cardProfileId) { this.cardProfileId = cardProfileId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getWalletId() { return walletId; }
    public void setWalletId(String walletId) { this.walletId = walletId; }
    public String getOwnerRef() { return ownerRef; }
    public void setOwnerRef(String ownerRef) { this.ownerRef = ownerRef; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getCardStatus() { return cardStatus; }
    public void setCardStatus(String cardStatus) { this.cardStatus = cardStatus; }
    public String getTokenRef() { return tokenRef; }
    public void setTokenRef(String tokenRef) { this.tokenRef = tokenRef; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public String getExpiryMeta() { return expiryMeta; }
    public void setExpiryMeta(String expiryMeta) { this.expiryMeta = expiryMeta; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
