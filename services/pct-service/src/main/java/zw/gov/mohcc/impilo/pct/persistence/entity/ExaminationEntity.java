package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A structured physical examination event (brief.md §7).
 *
 * <p>Reusable across every care setting: the specialty packs configure which regions they ask for
 * rather than each defining a record of their own. See {@code V113__examination_framework.sql}.</p>
 */
@Entity
@Table(name = "pct_examinations")
public class ExaminationEntity {

    @Id
    @Column(name = "examination_id")
    private UUID examinationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    /** The PCT anchor. At least one of these is required (care-continuum-doctrine CC-5). */
    @Column(name = "journey_id")
    private UUID journeyId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "examined_at", nullable = false)
    private OffsetDateTime examinedAt;

    @Column(name = "examined_by", nullable = false)
    private String examinedBy;

    @Column(name = "facility_id")
    private String facilityId;

    @Column(name = "context_note")
    private String contextNote;

    @Column(name = "client_offline_id")
    private String clientOfflineId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (examinationId == null) examinationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (examinedAt == null) examinedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getExaminationId() { return examinationId; }
    public void setExaminationId(UUID examinationId) { this.examinationId = examinationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public UUID getJourneyId() { return journeyId; }
    public void setJourneyId(UUID journeyId) { this.journeyId = journeyId; }

    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }

    public OffsetDateTime getExaminedAt() { return examinedAt; }
    public void setExaminedAt(OffsetDateTime examinedAt) { this.examinedAt = examinedAt; }

    public String getExaminedBy() { return examinedBy; }
    public void setExaminedBy(String examinedBy) { this.examinedBy = examinedBy; }

    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }

    public String getContextNote() { return contextNote; }
    public void setContextNote(String contextNote) { this.contextNote = contextNote; }

    public String getClientOfflineId() { return clientOfflineId; }
    public void setClientOfflineId(String clientOfflineId) { this.clientOfflineId = clientOfflineId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
