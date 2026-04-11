package zw.gov.mohcc.impilo.clinical.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "source_sections")
public class SourceSectionEntity {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    private Integer pageNumber;
    private String sectionTitle;
    private String sectionPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public String getSectionPath() {
        return sectionPath;
    }

    public String getRawText() {
        return rawText;
    }
}
