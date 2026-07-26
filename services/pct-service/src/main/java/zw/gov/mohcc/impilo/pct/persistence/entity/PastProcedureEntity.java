package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Procedural history — what was done before, however learned. Not a procedure record. See V106__structured_history.sql. */
@Entity
@Table(name = "pct_past_procedures")
public class PastProcedureEntity {

    @Id
    @Column(name = "procedure_id")
    private UUID procedureId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "code_system")
    private String codeSystem;

    @Column(name = "procedure_type")
    private String procedureType;

    @Column(name = "procedure_date")
    private LocalDate procedureDate;

    @Column(name = "date_precision")
    private String datePrecision;

    @Column(name = "performer")
    private String performer;

    @Column(name = "facility")
    private String facility;

    @Column(name = "status")
    private String status;

    @Column(name = "source_episode_ref")
    private String sourceEpisodeRef;

    @Column(name = "notes")
    private String notes;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (procedureId == null) procedureId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
    }

    public UUID getProcedureId() { return procedureId; }
    public void setProcedureId(UUID v) { this.procedureId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getCodeSystem() { return codeSystem; }
    public void setCodeSystem(String v) { this.codeSystem = v; }
    public String getProcedureType() { return procedureType; }
    public void setProcedureType(String v) { this.procedureType = v; }
    public LocalDate getProcedureDate() { return procedureDate; }
    public void setProcedureDate(LocalDate v) { this.procedureDate = v; }
    public String getDatePrecision() { return datePrecision; }
    public void setDatePrecision(String v) { this.datePrecision = v; }
    public String getPerformer() { return performer; }
    public void setPerformer(String v) { this.performer = v; }
    public String getFacility() { return facility; }
    public void setFacility(String v) { this.facility = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getSourceEpisodeRef() { return sourceEpisodeRef; }
    public void setSourceEpisodeRef(String v) { this.sourceEpisodeRef = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
