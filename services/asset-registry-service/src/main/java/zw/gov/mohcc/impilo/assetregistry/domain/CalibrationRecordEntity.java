package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Calibration outcome record. PASS/FAIL/ADJUSTED drives readiness + unsafe-use policy. */
@Entity
@Table(name = "asr_calibration_record")
public class CalibrationRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "calibration_id")
    private UUID calibrationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "performed_by", length = 255)
    private String performedBy;

    @Column(name = "performed_on", nullable = false)
    private LocalDate performedOn;

    @Column(name = "next_due")
    private LocalDate nextDue;

    @Column(name = "certificate_ref", length = 255)
    private String certificateRef;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    protected CalibrationRecordEntity() {}

    public CalibrationRecordEntity(UUID tenantId, UUID equipmentId, String outcome, LocalDate performedOn) {
        this.tenantId = tenantId;
        this.equipmentId = equipmentId;
        this.outcome = outcome;
        this.performedOn = performedOn;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getCalibrationId() { return calibrationId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEquipmentId() { return equipmentId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public LocalDate getPerformedOn() { return performedOn; }
    public void setPerformedOn(LocalDate performedOn) { this.performedOn = performedOn; }
    public LocalDate getNextDue() { return nextDue; }
    public void setNextDue(LocalDate nextDue) { this.nextDue = nextDue; }
    public String getCertificateRef() { return certificateRef; }
    public void setCertificateRef(String certificateRef) { this.certificateRef = certificateRef; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
