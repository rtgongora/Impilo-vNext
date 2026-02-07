package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_contacts", schema = "varapi")
public class ProviderContactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "contact_type", length = 30)
    private String contactType;

    @Column(name = "value", length = 255)
    private String value;

    @Column(name = "verified")
    private Boolean verified;

    @Column(name = "primary_contact")
    private Boolean primaryContact;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Convenience methods

    public boolean isVerified() {
        return Boolean.TRUE.equals(verified);
    }

    public boolean isPrimaryContact() {
        return Boolean.TRUE.equals(primaryContact);
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getContactType() { return contactType; }
    public void setContactType(String contactType) { this.contactType = contactType; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public Boolean getPrimaryContact() { return primaryContact; }
    public void setPrimaryContact(Boolean primaryContact) { this.primaryContact = primaryContact; }

    public Instant getCreatedAt() { return createdAt; }
}
