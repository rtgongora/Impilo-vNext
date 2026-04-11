package zw.gov.mohcc.impilo.clinical.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "extraction_jobs")
public class ExtractionJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Column(nullable = false)
    private String status = "QUEUED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_items_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode proposedItemsJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getStatus() {
        return status;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setProposedItemsJson(JsonNode proposedItemsJson) {
        this.proposedItemsJson = proposedItemsJson;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
