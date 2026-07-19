package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One staged employee/dependant in a roster batch (spec §24). */
@Entity
@Table(name = "cv_employer_roster_rows")
public class EmployerRosterRowEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "batch_id", nullable = false) private UUID batchId;
    @Column(name = "client_id", nullable = false, length = 255) private String clientId;
    @Column(name = "member_number", length = 64) private String memberNumber;
    @Column(name = "relationship", nullable = false, length = 32) private String relationship = "SELF";
    @Column(name = "validation_status", nullable = false, length = 16) private String validationStatus = "PENDING";
    @Column(name = "validation_error", length = 255) private String validationError;
    @Column(name = "applied_membership_id") private UUID appliedMembershipId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public EmployerRosterRowEntity() {}

    public static EmployerRosterRowEntity create(UUID tenantId, UUID batchId, String clientId,
                                                 String memberNumber, String relationship) {
        EmployerRosterRowEntity r = new EmployerRosterRowEntity();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.batchId = batchId;
        r.clientId = clientId;
        r.memberNumber = memberNumber;
        if (relationship != null && !relationship.isBlank()) r.relationship = relationship;
        return r;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBatchId() { return batchId; }
    public String getClientId() { return clientId; }
    public String getMemberNumber() { return memberNumber; }
    public String getRelationship() { return relationship; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String v) { this.validationStatus = v; }
    public String getValidationError() { return validationError; }
    public void setValidationError(String v) { this.validationError = v; }
    public UUID getAppliedMembershipId() { return appliedMembershipId; }
    public void setAppliedMembershipId(UUID v) { this.appliedMembershipId = v; }
}
