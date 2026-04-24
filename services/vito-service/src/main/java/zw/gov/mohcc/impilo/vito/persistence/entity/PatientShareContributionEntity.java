package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "patient_share_contribution", schema = "vito")
public class PatientShareContributionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_grant_id", nullable = false)
    private Long accessGrantId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "external_profile_id")
    private Long externalProfileId;

    @Column(name = "temporary_provider_public_id", length = 26)
    private String temporaryProviderPublicId;

    @Column(name = "target_record_type", nullable = false, length = 64)
    private String targetRecordType;

    @Column(name = "target_record_id")
    private String targetRecordId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "summary")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "trust_level_at_contribution", length = 64)
    private String trustLevelAtContribution;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
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

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getExternalProfileId() {
        return externalProfileId;
    }

    public void setExternalProfileId(Long externalProfileId) {
        this.externalProfileId = externalProfileId;
    }

    public String getTemporaryProviderPublicId() {
        return temporaryProviderPublicId;
    }

    public void setTemporaryProviderPublicId(String temporaryProviderPublicId) {
        this.temporaryProviderPublicId = temporaryProviderPublicId;
    }

    public String getTargetRecordType() {
        return targetRecordType;
    }

    public void setTargetRecordType(String targetRecordType) {
        this.targetRecordType = targetRecordType;
    }

    public String getTargetRecordId() {
        return targetRecordId;
    }

    public void setTargetRecordId(String targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getTrustLevelAtContribution() {
        return trustLevelAtContribution;
    }

    public void setTrustLevelAtContribution(String trustLevelAtContribution) {
        this.trustLevelAtContribution = trustLevelAtContribution;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
