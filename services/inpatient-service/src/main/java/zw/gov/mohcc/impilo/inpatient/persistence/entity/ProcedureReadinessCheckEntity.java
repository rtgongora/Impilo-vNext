package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One owner-routed readiness check (room/team/equipment/pack/blood/anaesthesia/consent) for a theatre episode. */
@Entity
@Table(name = "procedure_readiness_check", schema = "inpatient")
public class ProcedureReadinessCheckEntity {

    @Id
    @Column(name = "readiness_id")
    private UUID readinessId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(name = "owner_service", nullable = false)
    private String ownerService;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "owner_ref")
    private String ownerRef;

    @Column(name = "blockers_json", columnDefinition = "TEXT")
    private String blockersJson;

    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    @Column(name = "checked_by")
    private String checkedBy;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;

    @PrePersist
    void prePersist() {
        if (readinessId == null) readinessId = UUID.randomUUID();
        if (checkedAt == null) checkedAt = OffsetDateTime.now();
    }

    public UUID getReadinessId() { return readinessId; }
    public void setReadinessId(UUID v) { this.readinessId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getDomain() { return domain; }
    public void setDomain(String v) { this.domain = v; }
    public String getOwnerService() { return ownerService; }
    public void setOwnerService(String v) { this.ownerService = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getOwnerRef() { return ownerRef; }
    public void setOwnerRef(String v) { this.ownerRef = v; }
    public String getBlockersJson() { return blockersJson; }
    public void setBlockersJson(String v) { this.blockersJson = v; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String v) { this.detailJson = v; }
    public String getCheckedBy() { return checkedBy; }
    public void setCheckedBy(String v) { this.checkedBy = v; }
    public OffsetDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(OffsetDateTime v) { this.checkedAt = v; }
}
