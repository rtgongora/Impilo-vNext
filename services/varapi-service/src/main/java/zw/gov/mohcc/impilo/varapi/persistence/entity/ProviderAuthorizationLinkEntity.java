package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Authoritative HID ↔ Provider ID binding record (D-P1), mirroring the
 * client-side {@code vito.client_authorization_link} pattern.
 *
 * <p>Exactly one ACTIVE link per provider (partial unique index in V024);
 * rebinds (recovery, adjudicated rebind) supersede the previous row — history
 * is never overwritten. {@code varapi.provider.claimed_health_id} remains a
 * denormalized pointer to the current binding for cheap reads.</p>
 */
@Entity
@Table(name = "provider_authorization_link", schema = "varapi")
public class ProviderAuthorizationLinkEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_REVOKED = "REVOKED";

    public static final String TYPE_CLAIM = "CLAIM";
    public static final String TYPE_RECOVERY = "RECOVERY";
    public static final String TYPE_ADJUDICATED_REBIND = "ADJUDICATED_REBIND";
    public static final String TYPE_GOVERNANCE_UNBIND = "GOVERNANCE_UNBIND";

    @Id
    @Column(name = "link_id", nullable = false, updatable = false)
    private UUID linkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "provider_public_id", nullable = false)
    private String providerPublicId;

    @Column(name = "impilo_health_id", nullable = false)
    private UUID impiloHealthId;

    @Column(name = "link_type", nullable = false)
    private String linkType;

    @Column(name = "status", nullable = false)
    private String status = STATUS_ACTIVE;

    @Column(name = "evidence_ref")
    private String evidenceRef;

    @Column(name = "assurance_outcome")
    private String assuranceOutcome;

    @Column(name = "proofing_channel")
    private String proofingChannel;

    @Column(name = "bound_at", nullable = false)
    private Instant boundAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "actor_ref")
    private String actorRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (linkId == null) {
            linkId = UUID.randomUUID();
        }
        if (boundAt == null) {
            boundAt = Instant.now();
        }
        createdAt = Instant.now();
    }

    public UUID getLinkId() { return linkId; }
    public void setLinkId(UUID linkId) { this.linkId = linkId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getProviderPublicId() { return providerPublicId; }
    public void setProviderPublicId(String providerPublicId) { this.providerPublicId = providerPublicId; }
    public UUID getImpiloHealthId() { return impiloHealthId; }
    public void setImpiloHealthId(UUID impiloHealthId) { this.impiloHealthId = impiloHealthId; }
    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
    public String getAssuranceOutcome() { return assuranceOutcome; }
    public void setAssuranceOutcome(String assuranceOutcome) { this.assuranceOutcome = assuranceOutcome; }
    public String getProofingChannel() { return proofingChannel; }
    public void setProofingChannel(String proofingChannel) { this.proofingChannel = proofingChannel; }
    public Instant getBoundAt() { return boundAt; }
    public void setBoundAt(Instant boundAt) { this.boundAt = boundAt; }
    public Instant getSupersededAt() { return supersededAt; }
    public void setSupersededAt(Instant supersededAt) { this.supersededAt = supersededAt; }
    public String getActorRef() { return actorRef; }
    public void setActorRef(String actorRef) { this.actorRef = actorRef; }
    public Instant getCreatedAt() { return createdAt; }
}
