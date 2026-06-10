package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_import_row")
public class ImportRowEntity {
    @Id private UUID id;
    @Column(name = "import_batch_id", nullable = false) private UUID importBatchId;
    @Column(name = "row_number", nullable = false) private int rowNumber;
    @Column(name = "raw_payload_json", columnDefinition = "TEXT") private String rawPayloadJson;
    @Column(name = "normalized_payload_json", columnDefinition = "TEXT") private String normalizedPayloadJson;
    @Column(name = "validation_status") private String validationStatus;
    @Column(name = "match_status") private String matchStatus;
    @Column(name = "precheck_status") private String precheckStatus;
    @Column(name = "outcome") private String outcome;
    @Column(name = "invitation_id") private String invitationId;
    @Column(name = "exception_summary", columnDefinition = "TEXT") private String exceptionSummary;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ImportRowEntity() {}

    public void init(UUID batchId, int rowNumber, String rawJson) {
        this.id = UUID.randomUUID();
        this.importBatchId = batchId;
        this.rowNumber = rowNumber;
        this.rawPayloadJson = rawJson;
        this.normalizedPayloadJson = rawJson;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getImportBatchId() { return importBatchId; }
    public String getNormalizedPayloadJson() { return normalizedPayloadJson; }
    public String getInvitationId() { return invitationId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; touch(); }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; touch(); }
    public void setPrecheckStatus(String precheckStatus) { this.precheckStatus = precheckStatus; touch(); }
    public void setInvitationId(String invitationId) { this.invitationId = invitationId; touch(); }
    public void setNormalizedPayloadJson(String normalizedPayloadJson) { this.normalizedPayloadJson = normalizedPayloadJson; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
