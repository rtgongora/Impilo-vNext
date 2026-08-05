package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A service responsibility profile (v1.3.8 §3A.1): who operates a deployment, and — as a
 * separate matter — who is legally responsible for it.
 *
 * <p>The operator fields below say who <em>runs</em> something. They never say who answers
 * for it. §3A.2 makes this load-bearing: <em>no authority may be inferred from any operator
 * field</em>, so a party may be infrastructure operator, platform operator and key custodian
 * at once and still be nobody's controller.
 *
 * <p>{@code dataControllerId} is nullable because §26.2 #1 is undecided. A NOT NULL column
 * could only ever be satisfied by guessing an undetermined legal fact, and §3A.4 requires
 * that an unresolvable controller be refused and flagged rather than defaulted.
 */
@Entity
@Table(name = "service_responsibility_profile", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ServiceResponsibilityProfileEntity {

    @Id
    private UUID responsibilityProfileId;

    @Column(nullable = false, length = 64)
    private String profileCode;

    @Column(nullable = false, length = 32)
    private String hostingModel;

    // ── Operator fields: capability, never authority (§3A.2) ────────────────
    private UUID infrastructureOperatorId;
    private UUID platformOperatorId;
    private UUID applicationOperatorId;
    private UUID dataProcessorId;
    private UUID identityOperatorId;
    private UUID keyCustodianId;
    private UUID backupOperatorId;
    private UUID releaseApproverId;
    private UUID securityMonitoringOperatorId;

    // ── Authority fields: the only sources of authority (§3A.2) ─────────────
    private UUID dataControllerId;
    private UUID clinicalGovernanceAuthorityId;

    /** UNDETERMINED | PENDING_GOVERNANCE_REVIEW | RESOLVED */
    @Column(nullable = false, length = 32)
    private String controllerResolutionState = "UNDETERMINED";

    private UUID supportAccessPolicyId;
    private UUID maintenanceWindowPolicyId;

    /** DRAFT | VALIDATED | PENDING_APPROVAL | ACTIVE | SUSPENDED | EXPIRED | TERMINATED | SUPERSEDED */
    @Column(nullable = false, length = 24)
    private String status = "DRAFT";

    @Column(nullable = false)
    private Integer version = 1;

    private UUID supersedesProfileId;
    private String supersessionReason;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;

    @Column(length = 128)
    private String approvedByActor;
    private OffsetDateTime approvedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(nullable = false, length = 128)
    private String createdByActor;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion = 0;

    /** A profile is editable only before it becomes authority-bearing (§Scope C). */
    public boolean isMutable() {
        return "DRAFT".equals(status) || "VALIDATED".equals(status);
    }

    public boolean hasResolvedController() {
        return "RESOLVED".equals(controllerResolutionState) && dataControllerId != null;
    }
}
