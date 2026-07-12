package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit of every regulatory-profile change. An SQL table (not a
 * JSONB blob on the profile) so the reconciliation dashboard and provenance
 * queries can aggregate over changes.
 */
@Entity
@Table(name = "facility_regulatory_profile_history", schema = "tuso")
public class FacilityRegulatoryProfileHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id", nullable = false, updatable = false)
    private Long historyId;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "change_kind", nullable = false, length = 48)
    private String changeKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prev_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> prevSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> newSnapshot;

    @Column(name = "actor", length = 255)
    private String actor;

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    void prePersist() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public Long getHistoryId() { return historyId; }
    public UUID getProfileId() { return profileId; }
    public void setProfileId(UUID profileId) { this.profileId = profileId; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getChangeKind() { return changeKind; }
    public void setChangeKind(String changeKind) { this.changeKind = changeKind; }
    public Map<String, Object> getPrevSnapshot() { return prevSnapshot; }
    public void setPrevSnapshot(Map<String, Object> prevSnapshot) { this.prevSnapshot = prevSnapshot; }
    public Map<String, Object> getNewSnapshot() { return newSnapshot; }
    public void setNewSnapshot(Map<String, Object> newSnapshot) { this.newSnapshot = newSnapshot; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
}
