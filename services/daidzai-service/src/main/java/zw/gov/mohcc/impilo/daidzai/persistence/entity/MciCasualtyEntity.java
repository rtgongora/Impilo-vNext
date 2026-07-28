package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An individual casualty within an MCI incident (V202, W11) — genuinely absent before this table;
 * {@link EmergencyIncidentEntity} models exactly one subject per row (see V202's own header).
 */
@Entity
@Table(name = "dai_mci_casualty", schema = "daidzai")
public class MciCasualtyEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "incident_id", nullable = false) private UUID incidentId;
    @Column(name = "site_id") private UUID siteId;
    @Column(name = "casualty_reference", nullable = false, length = 48) private String casualtyReference;
    @Column(name = "sieve_category", nullable = false, length = 16) private String sieveCategory = "UNASSESSED";
    @Column(name = "subject_identity_mode", nullable = false, length = 24) private String subjectIdentityMode = "UNKNOWN";
    @Column(name = "subject_cpid", length = 128) private String subjectCpid;
    @Column(name = "subject_temp_ref", length = 128) private String subjectTempRef;
    @Column(name = "status", nullable = false, length = 24) private String status = "ON_SCENE";
    @Column(name = "destination_facility_id") private UUID destinationFacilityId;
    @Column(name = "emergency_episode_id") private UUID emergencyEpisodeId;
    @Column(name = "tagged_by", nullable = false, length = 128) private String taggedBy;
    @Column(name = "tagged_at", nullable = false) private OffsetDateTime taggedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (taggedAt == null) taggedAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID v) { this.incidentId = v; }
    public UUID getSiteId() { return siteId; }
    public void setSiteId(UUID v) { this.siteId = v; }
    public String getCasualtyReference() { return casualtyReference; }
    public void setCasualtyReference(String v) { this.casualtyReference = v; }
    public String getSieveCategory() { return sieveCategory; }
    public void setSieveCategory(String v) { this.sieveCategory = v; }
    public String getSubjectIdentityMode() { return subjectIdentityMode; }
    public void setSubjectIdentityMode(String v) { this.subjectIdentityMode = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getSubjectTempRef() { return subjectTempRef; }
    public void setSubjectTempRef(String v) { this.subjectTempRef = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public UUID getDestinationFacilityId() { return destinationFacilityId; }
    public void setDestinationFacilityId(UUID v) { this.destinationFacilityId = v; }
    public UUID getEmergencyEpisodeId() { return emergencyEpisodeId; }
    public void setEmergencyEpisodeId(UUID v) { this.emergencyEpisodeId = v; }
    public String getTaggedBy() { return taggedBy; }
    public void setTaggedBy(String v) { this.taggedBy = v; }
    public OffsetDateTime getTaggedAt() { return taggedAt; }
    public void setTaggedAt(OffsetDateTime v) { this.taggedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
