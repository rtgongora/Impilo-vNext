package zw.gov.mohcc.impilo.shareslip.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Share audit trail entity — records every action on a share link.
 *
 * Actions: CREATED, OTP_SENT, OTP_VERIFIED, CLAIMED, EXPIRED, REVOKED.
 * Immutable after creation — audit entries are never updated or deleted.
 */
@Entity
@Table(name = "share_audit", schema = "share_slip")
public class ShareAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "details", columnDefinition = "jsonb")
    private String details = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getLinkId() { return linkId; }
    public void setLinkId(UUID linkId) { this.linkId = linkId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
