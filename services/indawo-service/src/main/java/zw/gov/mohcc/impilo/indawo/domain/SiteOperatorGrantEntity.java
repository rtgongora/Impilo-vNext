package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * SJ1 (D-L4): a PERSON's role-scoped, expiring, evidence-backed management
 * grant on a site — the Indawo mirror of tuso's facility_admin_appointment.
 * Distinct from {@link SiteOperatorEntity} (the operator organisation facts).
 * Operators manage approved parts of the record; they can never edit
 * inspection findings or regulator-owned decisions.
 */
@Entity
@Table(name = "ind_site_operator_grant")
public class SiteOperatorGrantEntity {

    public static final Set<String> ROLE_SCOPES = Set.of(
            "SITE_VIEWER", "SITE_OPERATOR", "SITE_ADMINISTRATOR", "REGULATORY_LIAISON");

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_REJECTED = "REJECTED";
    public static final String STATE_REVOKED = "REVOKED";
    public static final String STATE_EXPIRED = "EXPIRED";

    public static final String CLAIM_TYPE_NEW = "NEW";
    public static final String CLAIM_TYPE_RECOVERY = "RECOVERY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "person_health_id", nullable = false, length = 128)
    private String personHealthId;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "approval_state", nullable = false, length = 16)
    private String approvalState = STATE_PENDING;

    @Column(name = "claim_type", nullable = false, length = 16)
    private String claimType = CLAIM_TYPE_NEW;

    @Column(name = "evidence_ref", nullable = false, length = 512)
    private String evidenceRef;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "appointed_by")
    private String appointedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getSiteId() { return siteId; }
    public void setSiteId(UUID siteId) { this.siteId = siteId; }
    public String getPersonHealthId() { return personHealthId; }
    public void setPersonHealthId(String personHealthId) { this.personHealthId = personHealthId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getApprovalState() { return approvalState; }
    public void setApprovalState(String approvalState) { this.approvalState = approvalState; }
    public String getClaimType() { return claimType; }
    public void setClaimType(String claimType) { this.claimType = claimType; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public String getAppointedBy() { return appointedBy; }
    public void setAppointedBy(String appointedBy) { this.appointedBy = appointedBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
