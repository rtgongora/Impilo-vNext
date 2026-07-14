package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-member enrolment into a subsidy programme — the CPID ↔ subsidy-programme link.
 *
 * @deprecated V013 unified this model into the ledgered
 * {@link SubsidyEnrolmentEntity} (cv_subsidy_enrolments), which now carries
 * {@code exemption_category}. The cv_subsidy_enrollments table is READ-ONLY
 * (DB trigger) and retained only for audit until a later drop migration.
 * Do not write through this entity.
 */
@Deprecated(since = "V013", forRemoval = true)
@Entity
@Table(name = "cv_subsidy_enrollments")
public class SubsidyEnrollmentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "subsidy_program_id", nullable = false)
    private UUID subsidyProgramId;

    @Column(name = "exemption_category", nullable = false, length = 64)
    private String exemptionCategory;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SubsidyEnrollmentEntity() {}

    public SubsidyEnrollmentEntity(UUID tenantId, String podId, String clientId,
                                   UUID subsidyProgramId, String exemptionCategory,
                                   LocalDate effectiveFrom) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.podId = podId != null ? podId : "national-spine";
        this.clientId = clientId;
        this.subsidyProgramId = subsidyProgramId;
        this.exemptionCategory = exemptionCategory;
        this.effectiveFrom = effectiveFrom;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getClientId() { return clientId; }
    public UUID getSubsidyProgramId() { return subsidyProgramId; }
    public String getExemptionCategory() { return exemptionCategory; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = OffsetDateTime.now(); }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; this.updatedAt = OffsetDateTime.now(); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
