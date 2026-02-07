package zw.gov.mohcc.impilo.tshepo.identity.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores an encrypted, indirect reference to the MOSIP national identity system.
 *
 * <p>The link_ref (MOSIP token/handle) is:</p>
 * <ul>
 *   <li>Encrypted with AES-256-GCM using a service-level KEK before storage</li>
 *   <li>Hashed with SHA-256 for lookup purposes (stored in link_ref_hash)</li>
 * </ul>
 *
 * <p>The National ID is NEVER stored. Only the opaque MOSIP reference token
 * is kept, and even that is encrypted at rest.</p>
 */
@Entity
@Table(name = "mosip_link", schema = "tshepo_identity")
public class MosipLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "health_id", nullable = false)
    private UUID healthId;

    @Column(name = "link_ref_hash", nullable = false, length = 128)
    private String linkRefHash;

    @Column(name = "link_ref_encrypted", nullable = false)
    private byte[] linkRefEncrypted;

    @Column(name = "verification_status", nullable = false, length = 16)
    private String verificationStatus = "PENDING";

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "proofing_event_ref", length = 255)
    private String proofingEventRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getHealthId() { return healthId; }
    public void setHealthId(UUID healthId) { this.healthId = healthId; }

    public String getLinkRefHash() { return linkRefHash; }
    public void setLinkRefHash(String linkRefHash) { this.linkRefHash = linkRefHash; }

    public byte[] getLinkRefEncrypted() { return linkRefEncrypted; }
    public void setLinkRefEncrypted(byte[] linkRefEncrypted) { this.linkRefEncrypted = linkRefEncrypted; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public Instant getLinkedAt() { return linkedAt; }
    public void setLinkedAt(Instant linkedAt) { this.linkedAt = linkedAt; }

    public String getProofingEventRef() { return proofingEventRef; }
    public void setProofingEventRef(String proofingEventRef) { this.proofingEventRef = proofingEventRef; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
