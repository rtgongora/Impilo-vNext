package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Curated, versioned mapping of one HPA requirement code (tuso content) to a
 * sourcing category. Human-approved content; explicitly partial by design.
 */
@Entity
@Table(name = "msika_requirement_sourcing_map")
public class RequirementSourcingMapEntity {

    @Id
    @Column(name = "map_id", length = 26)
    private String mapId;

    @Column(name = "requirement_code", nullable = false, length = 64)
    private String requirementCode;

    @Column(name = "sourcing_category_code", nullable = false, length = 64)
    private String sourcingCategoryCode;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "APPROVED";

    @Column(name = "provenance", columnDefinition = "TEXT")
    private String provenance;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public String getMapId() { return mapId; }
    public void setMapId(String mapId) { this.mapId = mapId; }
    public String getRequirementCode() { return requirementCode; }
    public void setRequirementCode(String requirementCode) { this.requirementCode = requirementCode; }
    public String getSourcingCategoryCode() { return sourcingCategoryCode; }
    public void setSourcingCategoryCode(String sourcingCategoryCode) { this.sourcingCategoryCode = sourcingCategoryCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
