package zw.gov.mohcc.impilo.tshepo.offline.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offline_pack", schema = "tshepo_offline")
public class OfflinePackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "generated_for", nullable = false)
    private String generatedFor;

    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "jsonb")
    private String policySnapshot;

    @Column(name = "consent_snapshot", columnDefinition = "jsonb")
    private String consentSnapshot;

    @Column(name = "terminology_snapshot", columnDefinition = "jsonb")
    private String terminologySnapshot;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int version;

    // --- Getters and Setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getGeneratedFor() { return generatedFor; }
    public void setGeneratedFor(String generatedFor) { this.generatedFor = generatedFor; }
    public String getPolicySnapshot() { return policySnapshot; }
    public void setPolicySnapshot(String policySnapshot) { this.policySnapshot = policySnapshot; }
    public String getConsentSnapshot() { return consentSnapshot; }
    public void setConsentSnapshot(String consentSnapshot) { this.consentSnapshot = consentSnapshot; }
    public String getTerminologySnapshot() { return terminologySnapshot; }
    public void setTerminologySnapshot(String terminologySnapshot) { this.terminologySnapshot = terminologySnapshot; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
