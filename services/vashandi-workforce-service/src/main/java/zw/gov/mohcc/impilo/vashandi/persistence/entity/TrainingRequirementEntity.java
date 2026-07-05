package zw.gov.mohcc.impilo.vashandi.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Governed mapping: role template → required native Fundo course, with the PO's
 * graduated enforcement level (ADVISORY | SOFT | HARD). Vashandi owns the mapping;
 * satisfaction is evaluated by the learning-service training-gate.
 */
@Entity
@Table(name = "vsh_training_requirement", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class TrainingRequirementEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 128)
    private String roleTemplateId;

    @Column(nullable = false, length = 128)
    private String courseCode;

    @Column(nullable = false, length = 16)
    private String enforcementLevel = "ADVISORY";

    @Column(nullable = false)
    private boolean active = true;

    private String note;
    private String createdBy;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
