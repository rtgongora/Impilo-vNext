package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patient_share_revocation", schema = "vito")
public class PatientShareRevocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_request_id")
    private Long shareRequestId;

    @Column(name = "access_grant_id")
    private Long accessGrantId;

    @Column(name = "revoked_by", nullable = false)
    private String revokedBy;

    @Column(name = "reason")
    private String reason;

    @Column(name = "revoked_at", nullable = false, updatable = false)
    private OffsetDateTime revokedAt;

    @PrePersist
    void onCreate() {
        revokedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShareRequestId() {
        return shareRequestId;
    }

    public void setShareRequestId(Long shareRequestId) {
        this.shareRequestId = shareRequestId;
    }

    public Long getAccessGrantId() {
        return accessGrantId;
    }

    public void setAccessGrantId(Long accessGrantId) {
        this.accessGrantId = accessGrantId;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(String revokedBy) {
        this.revokedBy = revokedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }
}
