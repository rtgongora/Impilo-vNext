package zw.gov.mohcc.impilo.clinical.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "source_documents")
public class SourceDocumentEntity {

    @Id
    private UUID id;

    private String title;
    private String sourceType;
    private String issuingAuthority;
    private LocalDate effectiveDate;
    private LocalDate publicationDate;
    private String versionLabel;
    private String status;
    private String language;
    private String fileReference;
    private String contentHash;
    private String ingestionStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public String getStatus() {
        return status;
    }
}
