package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Pre-licensing practice establishment case (ROM-W6). Facility created only on approval. */
@Entity
@Table(name = "practice_establishment_case", schema = "tuso")
public class PracticeEstablishmentCaseEntity {
    @Id @Column(name = "establishment_id") private UUID establishmentId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "applicant_health_id", nullable = false) private UUID applicantHealthId;
    @Column(name = "proposed_name", nullable = false) private String proposedName;
    @Column(name = "category_code") private String categoryCode;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "ownership") private String ownership;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "directors") private String directors;
    @Column(name = "nominated_pic_ref") private String nominatedPicRef;
    @Column(name = "proposed_province") private String proposedProvince;
    @Column(name = "proposed_district") private String proposedDistrict;
    @Column(name = "status", nullable = false) private String status = "DRAFT";
    @Column(name = "created_facility_uuid") private UUID createdFacilityUuid;
    @Column(name = "application_id") private UUID applicationId;
    @Column(name = "decision_notes") private String decisionNotes;
    @Column(name = "decided_by") private String decidedBy;
    @Column(name = "decided_at") private OffsetDateTime decidedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void oc() { if (establishmentId == null) establishmentId = UUID.randomUUID(); createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void ou() { updatedAt = Instant.now(); }

    public UUID getEstablishmentId() { return establishmentId; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getApplicantHealthId() { return applicantHealthId; } public void setApplicantHealthId(UUID v) { this.applicantHealthId = v; }
    public String getProposedName() { return proposedName; } public void setProposedName(String v) { this.proposedName = v; }
    public String getCategoryCode() { return categoryCode; } public void setCategoryCode(String v) { this.categoryCode = v; }
    public String getOwnership() { return ownership; } public void setOwnership(String v) { this.ownership = v; }
    public String getDirectors() { return directors; } public void setDirectors(String v) { this.directors = v; }
    public String getNominatedPicRef() { return nominatedPicRef; } public void setNominatedPicRef(String v) { this.nominatedPicRef = v; }
    public String getProposedProvince() { return proposedProvince; } public void setProposedProvince(String v) { this.proposedProvince = v; }
    public String getProposedDistrict() { return proposedDistrict; } public void setProposedDistrict(String v) { this.proposedDistrict = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public UUID getCreatedFacilityUuid() { return createdFacilityUuid; } public void setCreatedFacilityUuid(UUID v) { this.createdFacilityUuid = v; }
    public UUID getApplicationId() { return applicationId; } public void setApplicationId(UUID v) { this.applicationId = v; }
    public String getDecisionNotes() { return decisionNotes; } public void setDecisionNotes(String v) { this.decisionNotes = v; }
    public String getDecidedBy() { return decidedBy; } public void setDecidedBy(String v) { this.decidedBy = v; }
    public OffsetDateTime getDecidedAt() { return decidedAt; } public void setDecidedAt(OffsetDateTime v) { this.decidedAt = v; }
}
