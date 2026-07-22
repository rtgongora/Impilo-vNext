package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** First-class good-standing status per (provider, council) (ROM-W3, R4). */
@Entity
@Table(name = "provider_good_standing", schema = "varapi")
public class ProviderGoodStandingEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "provider_id", nullable = false) private Long providerId;
    @Column(name = "council_id", nullable = false) private Long councilId;
    @Column(name = "standing", nullable = false, length = 32) private String standing = "GOOD";
    @Column(name = "as_of", nullable = false) private OffsetDateTime asOf;
    @Column(name = "derivation", columnDefinition = "TEXT") private String derivation;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() {
        Instant now = Instant.now();
        createdAt = now; updatedAt = now;
        if (asOf == null) { asOf = OffsetDateTime.now(); }
    }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID t) { this.tenantId = t; }
    public Long getProviderId() { return providerId; } public void setProviderId(Long p) { this.providerId = p; }
    public Long getCouncilId() { return councilId; } public void setCouncilId(Long c) { this.councilId = c; }
    public String getStanding() { return standing; } public void setStanding(String s) { this.standing = s; }
    public OffsetDateTime getAsOf() { return asOf; } public void setAsOf(OffsetDateTime a) { this.asOf = a; }
    public String getDerivation() { return derivation; } public void setDerivation(String d) { this.derivation = d; }
}
