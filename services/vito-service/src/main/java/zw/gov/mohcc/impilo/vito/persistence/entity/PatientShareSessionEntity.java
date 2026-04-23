package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patient_share_session", schema = "vito")
public class PatientShareSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_grant_id", nullable = false)
    private Long accessGrantId;

    @Column(name = "session_token_hash", nullable = false, unique = true)
    private String sessionTokenHash;

    @Column(name = "external_profile_id")
    private Long externalProfileId;

    @Column(name = "session_status", nullable = false, length = 32)
    private String sessionStatus = "PENDING_OTP";

    @Column(name = "opened_at", nullable = false, updatable = false)
    private OffsetDateTime openedAt;

    @Column(name = "last_active_at", nullable = false)
    private OffsetDateTime lastActiveAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "step_up_verified", nullable = false)
    private boolean stepUpVerified = true;

    @PrePersist
    void onCreate() {
        OffsetDateTime n = OffsetDateTime.now();
        openedAt = n;
        lastActiveAt = n;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccessGrantId() {
        return accessGrantId;
    }

    public void setAccessGrantId(Long accessGrantId) {
        this.accessGrantId = accessGrantId;
    }

    public String getSessionTokenHash() {
        return sessionTokenHash;
    }

    public void setSessionTokenHash(String sessionTokenHash) {
        this.sessionTokenHash = sessionTokenHash;
    }

    public Long getExternalProfileId() {
        return externalProfileId;
    }

    public void setExternalProfileId(Long externalProfileId) {
        this.externalProfileId = externalProfileId;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public OffsetDateTime getOpenedAt() {
        return openedAt;
    }

    public OffsetDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(OffsetDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public boolean isStepUpVerified() {
        return stepUpVerified;
    }

    public void setStepUpVerified(boolean stepUpVerified) {
        this.stepUpVerified = stepUpVerified;
    }
}
