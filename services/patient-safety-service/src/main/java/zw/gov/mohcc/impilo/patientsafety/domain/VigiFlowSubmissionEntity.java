package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Record of a MANUAL VigiFlow entry. Impilo does not submit to VigiFlow automatically; an MCAZ
 * officer keys the case into the external WHO-UMC VigiFlow system and records the reference id here.
 * {@code entryMode} is always MANUAL in this PoC — there is no automated transmission path.
 */
@Entity
@Table(name = "ps_vigiflow_submission")
public class VigiFlowSubmissionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "entry_mode", nullable = false, length = 16)
    private String entryMode = "MANUAL";
    @Column(name = "vigiflow_reference_id", nullable = false, length = 128)
    private String vigiflowReferenceId;
    @Column(name = "entered_by", length = 255)
    private String enteredBy;
    @Column(name = "note")
    private String note;
    @Column(name = "entered_at", nullable = false)
    private OffsetDateTime enteredAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected VigiFlowSubmissionEntity() {}

    public VigiFlowSubmissionEntity(UUID caseId, UUID tenantId, String vigiflowReferenceId) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.vigiflowReferenceId = vigiflowReferenceId;
        OffsetDateTime now = OffsetDateTime.now();
        this.enteredAt = now;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public UUID getTenantId() { return tenantId; }
    public String getEntryMode() { return entryMode; }
    public String getVigiflowReferenceId() { return vigiflowReferenceId; }
    public void setVigiflowReferenceId(String v) { this.vigiflowReferenceId = v; }
    public String getEnteredBy() { return enteredBy; }
    public void setEnteredBy(String v) { this.enteredBy = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public OffsetDateTime getEnteredAt() { return enteredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
