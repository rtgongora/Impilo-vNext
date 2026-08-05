package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A processing role assignment (v1.3.8 §3A.4): who holds which legal role, for which data
 * domain, for which purpose, over which scope, and for how long.
 *
 * <p>This exists because one field cannot express reality. The same institution can be
 * controller for its clinical operational record, joint controller for a shared programme,
 * processor for a national service it merely hosts, and subject to a separate statutory
 * disclosure authority for notifiable conditions — all at once.
 *
 * <p>A database exclusion constraint enforces the rule that must never bend: at most one
 * ACTIVE {@code LEGAL_CONTROLLER} per (scope, data domain, purpose) at any instant. Joint
 * control is expressed by {@code JOINT_CONTROLLER} rows sharing an arrangement, never by
 * two controllers.
 */
@Entity
@Table(name = "processing_role_assignment", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ProcessingRoleAssignmentEntity {

    @Id
    private UUID assignmentId;

    /** TRUST_DOMAIN | ORGANISATION | FACILITY | NODE | SERVICE_AGREEMENT | PROGRAMME */
    @Column(nullable = false, length = 24)
    private String scopeType;

    @Column(nullable = false)
    private UUID scopeId;

    @Column(nullable = false, length = 64)
    private String dataDomain;

    @Column(nullable = false, length = 64)
    private String purpose;

    @Column(nullable = false, length = 48)
    private String legalBasis;

    /**
     * LEGAL_CONTROLLER | JOINT_CONTROLLER | PROCESSOR | SUB_PROCESSOR | SOURCE_AUTHORITY |
     * CUSTODIAN | RIGHTSHOLDER | DISCLOSURE_AUTHORITY | CLINICAL_GOVERNANCE
     */
    @Column(nullable = false, length = 32)
    private String roleType;

    @Column(nullable = false)
    private UUID partyId;

    private UUID jointArrangementId;

    /** The statute, contract or determination relied on. */
    private String instrumentReference;

    @Column(nullable = false)
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;

    /** DRAFT | ACTIVE | SUPERSEDED | WITHDRAWN */
    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(nullable = false, length = 128)
    private String recordedBy;

    @Column(nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    @Version
    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion = 0;

    public boolean isActiveAt(OffsetDateTime instant) {
        if (!"ACTIVE".equals(status)) {
            return false;
        }
        boolean started = !effectiveFrom.isAfter(instant);
        boolean notEnded = effectiveTo == null || effectiveTo.isAfter(instant);
        return started && notEnded;
    }
}
