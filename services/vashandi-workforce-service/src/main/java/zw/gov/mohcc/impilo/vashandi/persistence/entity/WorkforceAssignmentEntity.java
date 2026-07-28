package zw.gov.mohcc.impilo.vashandi.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vsh_workforce_assignment", schema = "vashandi")
@Getter
@Setter
@NoArgsConstructor
public class WorkforceAssignmentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID workforceProfileId;

    @Column(nullable = false, length = 64)
    private String assignmentType;

    private UUID organisationId;
    private UUID facilityId;
    private String departmentId;
    private String unitId;
    private String programmeId;
    private UUID workspaceId;
    private String roleTemplateId;
    private UUID supervisorProfileId;

    /**
     * Cross-service reference to support-service sup_support_team.team_id (A3).
     * Set only when assignmentType="support"; the support authority itself
     * (tier, scope, permitted actions, elevated access) lives in
     * support-service, not here — Vashandi records only the posting fact.
     */
    private UUID supportTeamId;

    /** Cross-service reference to support-service sup_support_assignment.assignment_id. */
    private UUID supportAssignmentId;

    @Column(nullable = false, length = 32)
    private String status = "draft";

    private LocalDate startDate;
    private LocalDate endDate;
    private String sourceAuthority;

    /**
     * How the provider is engaged (D-P7): PERMANENT | ROTATION | LOCUM |
     * OUTREACH | TELEMED | SPECIALIST_POOL | SUPERVISORY | TRAINING.
     * Non-permanent engagements require an endDate (V008 CHECK).
     */
    @Column(name = "engagement_type", length = 32)
    private String engagementType;

    @Column(nullable = false, length = 32)
    private String eligibilityStatus = "pending";

    private String opaDecisionId;
    private String createdBy;
    private String approvedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String auditMetadataJson;

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
