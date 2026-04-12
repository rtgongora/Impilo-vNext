package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_financial_documents")
public class FinancialDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id", nullable = false, unique = true)
    private UUID docId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "doc_type", nullable = false, length = 32)
    private String docType;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_ref", nullable = false, length = 128)
    private String subjectRef;

    @Column(name = "recipient_type", length = 32)
    private String recipientType;

    @Column(name = "recipient_ref", length = 128)
    private String recipientRef;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    private String contentJson = "{}";

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "status", length = 16)
    private String status = "GENERATED";

    @PrePersist
    protected void onCreate() {
        if (docId == null) {
            docId = UUID.randomUUID();
        }
        if (generatedAt == null) {
            generatedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getDocId() { return docId; }
    public void setDocId(UUID docId) { this.docId = docId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }
    public String getRecipientType() { return recipientType; }
    public void setRecipientType(String recipientType) { this.recipientType = recipientType; }
    public String getRecipientRef() { return recipientRef; }
    public void setRecipientRef(String recipientRef) { this.recipientRef = recipientRef; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
