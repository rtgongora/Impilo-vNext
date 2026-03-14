package zw.gov.mohcc.impilo.forms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fs_form_schema_versions")
public class FormSchemaVersionEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "form_schema_id", length = 36, nullable = false)
    private String formSchemaId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "schema_json", columnDefinition = "TEXT", nullable = false)
    private String schemaJson;

    @Column(name = "changelog", length = 1024)
    private String changelog;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFormSchemaId() { return formSchemaId; }
    public void setFormSchemaId(String formSchemaId) { this.formSchemaId = formSchemaId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }

    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = changelog; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
