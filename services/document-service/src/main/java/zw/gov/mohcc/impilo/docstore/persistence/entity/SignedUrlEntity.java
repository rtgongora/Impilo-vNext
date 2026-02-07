package zw.gov.mohcc.impilo.docstore.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the document_store.signed_urls table.
 *
 * Records every pre-signed URL generated for object access,
 * tracking the URL token, expiration, and access timestamps
 * for audit and security purposes.
 */
@Entity
@Table(name = "signed_urls", schema = "document_store")
public class SignedUrlEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_id", nullable = false)
    private UUID objectId;

    @Column(name = "url_token", nullable = false, unique = true, length = 64)
    private String urlToken;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "accessed_at")
    private OffsetDateTime accessedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getObjectId() { return objectId; }
    public void setObjectId(UUID objectId) { this.objectId = objectId; }

    public String getUrlToken() { return urlToken; }
    public void setUrlToken(String urlToken) { this.urlToken = urlToken; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getAccessedAt() { return accessedAt; }
    public void setAccessedAt(OffsetDateTime accessedAt) { this.accessedAt = accessedAt; }
}
