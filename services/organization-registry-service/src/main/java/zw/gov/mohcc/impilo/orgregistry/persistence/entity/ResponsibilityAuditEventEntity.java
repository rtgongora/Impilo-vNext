package zw.gov.mohcc.impilo.orgregistry.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only governance audit (v1.3.8 §Scope G). Records who did what to which governed
 * object, what was decided, and — when the answer was no — exactly why.
 *
 * <p>Refusals are audited as deliberately as permissions. A refusal nobody can see is
 * indistinguishable from an operation that silently did nothing.
 *
 * <p>Carries no clinical content, no record bodies and no secrets. Database triggers reject
 * UPDATE and DELETE: evidence that can be edited is not evidence.
 */
@Entity
@Table(name = "responsibility_audit_event", schema = "org_registry")
@Getter
@Setter
@NoArgsConstructor
public class ResponsibilityAuditEventEntity {

    @Id
    private UUID auditEventId;

    @Column(nullable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    @Column(nullable = false, length = 128)
    private String actorId;

    @Column(length = 128)
    private String actorAuthority;

    @Column(length = 24)
    private String actorScopeType;
    private UUID actorScopeId;

    @Column(nullable = false, length = 64)
    private String operation;

    @Column(nullable = false, length = 48)
    private String targetType;
    private UUID targetId;

    private UUID trustDomainId;
    private UUID organisationId;
    private UUID facilityId;

    private Integer previousVersion;
    private Integer newVersion;

    /** PERMITTED | REFUSED */
    @Column(nullable = false, length = 16)
    private String decision;

    @Column(length = 64)
    private String reasonCode;
    private String reasonDetail;

    private OffsetDateTime effectiveDate;

    @Column(length = 128)
    private String correlationId;

    @Column(length = 128)
    private String requestId;

    @Column(nullable = false, length = 64)
    private String sourceService = "organization-registry-service";
}
