package zw.gov.mohcc.impilo.tshepo.federation.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Persists federation pod registrations.
 *
 * Each pod registers with the national spine and receives an authority scope
 * that governs which data classes, facilities, and operations it may access.
 */
@Entity
@Table(name = "pod_registration", schema = "tshepo",
        uniqueConstraints = @UniqueConstraint(columnNames = "pod_id"))
public class PodRegistrationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_id", nullable = false, unique = true)
    private UUID registrationId;

    @Column(name = "pod_id", nullable = false, unique = true)
    private UUID podId;

    @Column(name = "pod_name", nullable = false)
    private String podName;

    @Column(name = "pod_certificate", nullable = false, columnDefinition = "TEXT")
    private String podCertificate;

    @Column(name = "capabilities", nullable = false, columnDefinition = "jsonb")
    private String capabilities;

    @Column(name = "region")
    private String region;

    @Column(name = "facility_ids", columnDefinition = "jsonb")
    private String facilityIds;

    @Column(name = "granted_data_classes", nullable = false, columnDefinition = "jsonb")
    private String grantedDataClasses;

    @Column(name = "max_offline_hours", nullable = false)
    private int maxOfflineHours;

    @Column(name = "merge_authority", nullable = false)
    private boolean mergeAuthority;

    @Column(name = "revocation_priority", nullable = false)
    private String revocationPriority;

    @Column(name = "heartbeat_interval_seconds", nullable = false)
    private int heartbeatIntervalSeconds;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PodStatus status;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @Column(name = "revocation_reason")
    private String revocationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (registrationId == null) registrationId = UUID.randomUUID();
        if (status == null) status = PodStatus.ACTIVE;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getRegistrationId() { return registrationId; }
    public void setRegistrationId(UUID registrationId) { this.registrationId = registrationId; }
    public UUID getPodId() { return podId; }
    public void setPodId(UUID podId) { this.podId = podId; }
    public String getPodName() { return podName; }
    public void setPodName(String podName) { this.podName = podName; }
    public String getPodCertificate() { return podCertificate; }
    public void setPodCertificate(String podCertificate) { this.podCertificate = podCertificate; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getFacilityIds() { return facilityIds; }
    public void setFacilityIds(String facilityIds) { this.facilityIds = facilityIds; }
    public String getGrantedDataClasses() { return grantedDataClasses; }
    public void setGrantedDataClasses(String grantedDataClasses) { this.grantedDataClasses = grantedDataClasses; }
    public int getMaxOfflineHours() { return maxOfflineHours; }
    public void setMaxOfflineHours(int maxOfflineHours) { this.maxOfflineHours = maxOfflineHours; }
    public boolean isMergeAuthority() { return mergeAuthority; }
    public void setMergeAuthority(boolean mergeAuthority) { this.mergeAuthority = mergeAuthority; }
    public String getRevocationPriority() { return revocationPriority; }
    public void setRevocationPriority(String revocationPriority) { this.revocationPriority = revocationPriority; }
    public int getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) { this.heartbeatIntervalSeconds = heartbeatIntervalSeconds; }
    public PodStatus getStatus() { return status; }
    public void setStatus(PodStatus status) { this.status = status; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public String getRevocationReason() { return revocationReason; }
    public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
