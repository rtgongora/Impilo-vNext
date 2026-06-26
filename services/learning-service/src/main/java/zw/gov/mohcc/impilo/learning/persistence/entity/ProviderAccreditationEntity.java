package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A provider's accreditation record (V024), routed to the regulator matching its kind. */
@Entity
@Table(name = "lrn_provider_accreditation")
public class ProviderAccreditationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "regulator", nullable = false, length = 255)
    private String regulator;

    @Column(name = "certificate_ref", length = 255)
    private String certificateRef;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "accredited_by", length = 255)
    private String accreditedBy;

    @Column(name = "accredited_at", nullable = false)
    private OffsetDateTime accreditedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (accreditedAt == null) accreditedAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getProviderId() { return providerId; }
    public void setProviderId(UUID providerId) { this.providerId = providerId; }
    public String getRegulator() { return regulator; }
    public void setRegulator(String regulator) { this.regulator = regulator; }
    public String getCertificateRef() { return certificateRef; }
    public void setCertificateRef(String certificateRef) { this.certificateRef = certificateRef; }
    public OffsetDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(OffsetDateTime validUntil) { this.validUntil = validUntil; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAccreditedBy() { return accreditedBy; }
    public void setAccreditedBy(String accreditedBy) { this.accreditedBy = accreditedBy; }
    public OffsetDateTime getAccreditedAt() { return accreditedAt; }
    public void setAccreditedAt(OffsetDateTime accreditedAt) { this.accreditedAt = accreditedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
