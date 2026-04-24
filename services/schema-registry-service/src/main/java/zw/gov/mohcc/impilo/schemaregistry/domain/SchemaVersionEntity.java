package zw.gov.mohcc.impilo.schemaregistry.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scr_schema_versions")
public class SchemaVersionEntity {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    @Column(name = "subject_id", nullable = false) private UUID subjectId;
    @Column(name = "version", nullable = false) private int version;
    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT") private String schemaJson;
    @Column(name = "fingerprint", nullable = false) private String fingerprint;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID v) { this.subjectId = v; }
    public int getVersion() { return version; }
    public void setVersion(int v) { this.version = v; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String v) { this.schemaJson = v; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String v) { this.fingerprint = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
