package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What was found in one region of one examination — or that it was deliberately not examined.
 *
 * <p>A region absent from this table was never considered at all, which is different again from
 * {@code NOT_EXAMINED}, where the examiner looked at the list and chose not to. Both are honest and
 * the read side must not merge them.</p>
 */
@Entity
@Table(name = "pct_examination_findings")
public class ExaminationFindingEntity {

    @Id
    @Column(name = "finding_id")
    private UUID findingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "examination_id", nullable = false)
    private UUID examinationId;

    @Column(name = "region", nullable = false)
    private String region;

    /** One of the six states. NOT_EXAMINED is never a quieter kind of NORMAL. */
    @Column(name = "state", nullable = false)
    private String state;

    /** What was found, or why it could not be. Required for every state but NORMAL and NOT_EXAMINED. */
    @Column(name = "detail")
    private String detail;

    @Column(name = "finding_code")
    private String findingCode;

    @Column(name = "graphic")
    private String graphic;

    @Column(name = "site")
    private String site;

    @Column(name = "laterality")
    private String laterality;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (findingId == null) findingId = UUID.randomUUID();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public UUID getFindingId() { return findingId; }
    public void setFindingId(UUID findingId) { this.findingId = findingId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getExaminationId() { return examinationId; }
    public void setExaminationId(UUID examinationId) { this.examinationId = examinationId; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getFindingCode() { return findingCode; }
    public void setFindingCode(String findingCode) { this.findingCode = findingCode; }

    public String getGraphic() { return graphic; }
    public void setGraphic(String graphic) { this.graphic = graphic; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public String getLaterality() { return laterality; }
    public void setLaterality(String laterality) { this.laterality = laterality; }

    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
