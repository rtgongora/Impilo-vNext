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
@Table(name = "card_health_data", schema = "mushe")
public class CardHealthDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_cpid", nullable = false, length = 128)
    private String patientCpid;

    @Column(name = "data_type", nullable = false, length = 32)
    private String dataType;

    @Column(name = "content_encrypted", nullable = false, columnDefinition = "BYTEA")
    private byte[] contentEncrypted;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "compression", length = 16)
    private String compression = "ZSTD";

    @Column(name = "encryption_key_ref", nullable = false, length = 128)
    private String encryptionKeyRef;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "source_encounter_id", length = 128)
    private String sourceEncounterId;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "synced_to_card_at")
    private OffsetDateTime syncedToCardAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = OffsetDateTime.now();
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

    public String getPatientCpid() {
        return patientCpid;
    }

    public void setPatientCpid(String patientCpid) {
        this.patientCpid = patientCpid;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public byte[] getContentEncrypted() {
        return contentEncrypted;
    }

    public void setContentEncrypted(byte[] contentEncrypted) {
        this.contentEncrypted = contentEncrypted;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public String getEncryptionKeyRef() {
        return encryptionKeyRef;
    }

    public void setEncryptionKeyRef(String encryptionKeyRef) {
        this.encryptionKeyRef = encryptionKeyRef;
    }

    public Integer getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Integer sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getSourceEncounterId() {
        return sourceEncounterId;
    }

    public void setSourceEncounterId(String sourceEncounterId) {
        this.sourceEncounterId = sourceEncounterId;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public OffsetDateTime getSyncedToCardAt() {
        return syncedToCardAt;
    }

    public void setSyncedToCardAt(OffsetDateTime syncedToCardAt) {
        this.syncedToCardAt = syncedToCardAt;
    }
}
