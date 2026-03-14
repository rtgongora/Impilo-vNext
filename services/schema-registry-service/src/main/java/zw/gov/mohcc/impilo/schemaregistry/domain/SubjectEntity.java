package zw.gov.mohcc.impilo.schemaregistry.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scr_subjects")
public class SubjectEntity {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    @Column(name = "subject_name", nullable = false, unique = true) private String subjectName;
    @Column(name = "compatibility", nullable = false) private String compatibility = "BACKWARD";
    @Column(name = "description") private String description;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String v) { this.subjectName = v; }
    public String getCompatibility() { return compatibility; }
    public void setCompatibility(String v) { this.compatibility = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
