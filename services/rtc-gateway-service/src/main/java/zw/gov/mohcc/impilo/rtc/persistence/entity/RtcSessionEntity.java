package zw.gov.mohcc.impilo.rtc.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "rtc_sessions", schema = "rtc")
public class RtcSessionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "room_name", nullable = false)
    private String roomName;

    @Column(name = "room_url", nullable = false)
    private String roomUrl;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "session_mode", length = 32)
    private String sessionMode;

    @Column(name = "owning_service", length = 32)
    private String owningService;

    @Column(name = "owning_ref", length = 128)
    private String owningRef;

    @Column(name = "patient_id")
    private String patientId;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "referral_id")
    private String referralId;

    @Column(name = "consent_reference")
    private String consentReference;

    @Column(name = "channel")
    private String channel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", nullable = false, columnDefinition = "jsonb")
    private Map<String, Boolean> capabilities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_policy", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> mediaPolicy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RtcSessionEntity() {}

    public static RtcSessionEntity fromRecord(RtcSessionRecord record) {
        RtcSessionEntity entity = new RtcSessionEntity();
        entity.setId(record.id());
        entity.setTenantId(record.tenantId());
        entity.setProvider(record.provider());
        entity.setRoomName(record.roomName());
        entity.setRoomUrl(record.roomUrl());
        entity.setStatus(record.status());
        entity.setSessionMode(record.sessionMode());
        entity.setOwningService(record.owningService());
        entity.setOwningRef(record.owningRef());
        entity.setPatientId(record.patientId());
        entity.setProviderId(record.providerId());
        entity.setEncounterId(record.encounterId());
        entity.setReferralId(record.referralId());
        entity.setConsentReference(record.consentReference());
        entity.setChannel(record.channel());
        entity.setCapabilities(record.capabilities());
        entity.setMediaPolicy(record.mediaPolicy());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }

    public RtcSessionRecord toRecord() {
        return new RtcSessionRecord(
                id,
                tenantId,
                provider,
                roomName,
                roomUrl,
                status,
                sessionMode,
                owningService,
                owningRef,
                patientId,
                providerId,
                encounterId,
                referralId,
                consentReference,
                channel,
                capabilities,
                mediaPolicy,
                createdAt,
                updatedAt);
    }

    @PrePersist
    @PreUpdate
    void touchTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getRoomUrl() { return roomUrl; }
    public void setRoomUrl(String roomUrl) { this.roomUrl = roomUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSessionMode() { return sessionMode; }
    public void setSessionMode(String sessionMode) { this.sessionMode = sessionMode; }
    public String getOwningService() { return owningService; }
    public void setOwningService(String owningService) { this.owningService = owningService; }
    public String getOwningRef() { return owningRef; }
    public void setOwningRef(String owningRef) { this.owningRef = owningRef; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public String getReferralId() { return referralId; }
    public void setReferralId(String referralId) { this.referralId = referralId; }
    public String getConsentReference() { return consentReference; }
    public void setConsentReference(String consentReference) { this.consentReference = consentReference; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Map<String, Boolean> getCapabilities() { return capabilities; }
    public void setCapabilities(Map<String, Boolean> capabilities) { this.capabilities = capabilities; }
    public Map<String, Object> getMediaPolicy() { return mediaPolicy; }
    public void setMediaPolicy(Map<String, Object> mediaPolicy) { this.mediaPolicy = mediaPolicy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
