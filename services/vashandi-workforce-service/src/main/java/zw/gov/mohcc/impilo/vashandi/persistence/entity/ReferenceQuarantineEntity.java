package zw.gov.mohcc.impilo.vashandi.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A free-text department_id/programme_id value that did not resolve against
 * its canonical registry (A5). Remediation surface, never a silent discard.
 */
@Entity
@Table(name = "vsh_reference_quarantine", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class ReferenceQuarantineEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID workforceAssignmentId;

    /** DEPARTMENT_ID | PROGRAMME_ID */
    @Column(nullable = false, length = 32)
    private String referenceField;

    @Column(nullable = false, length = 255)
    private String sourceValue;

    @Column(nullable = false, length = 255)
    private String reason = "NOT_FOUND_IN_CANONICAL_REGISTRY";

    @Column(nullable = false)
    private boolean resolved = false;

    private OffsetDateTime resolvedAt;
    private String resolvedBy;

    @Column(columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(nullable = false)
    private OffsetDateTime quarantinedAt = OffsetDateTime.now();
}
