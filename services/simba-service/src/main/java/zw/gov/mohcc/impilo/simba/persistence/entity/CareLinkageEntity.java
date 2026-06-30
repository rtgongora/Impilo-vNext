package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Wellness-to-care linkage. Simba records that a wellness journey identified risk and routed
 * it to a clinical owner (PCT / emergency / screening). The clinical workflow and results stay
 * with the owner; Simba tracks only the linkage + status.
 */
@Entity
@Table(name = "care_linkages", schema = "simba")
public class CareLinkageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "linkage_id", nullable = false, unique = true)
    private UUID linkageId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "trigger", nullable = false, length = 64)
    private String trigger;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "ROUTINE";

    @Column(name = "reason")
    private String reason;

    @Column(name = "target_owner", nullable = false, length = 32)
    private String targetOwner = "PCT";

    @Column(name = "external_ref", length = 128)
    private String externalRef;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (linkageId == null) {
            linkageId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getLinkageId() { return linkageId; }
    public void setLinkageId(UUID linkageId) { this.linkageId = linkageId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPersonCpid() { return personCpid; }
    public void setPersonCpid(String personCpid) { this.personCpid = personCpid; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getTargetOwner() { return targetOwner; }
    public void setTargetOwner(String targetOwner) { this.targetOwner = targetOwner; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
