package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A case-investigation line-list entry tied to an outbreak / alert (Indawo SoR).
 * Person reference is a pseudonymous CPID only — no PII in Indawo (doctrine).
 */
@Entity
@Table(name = "ind_case_investigations")
public class CaseInvestigationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "outbreak_id")
    private UUID outbreakId;

    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "cpid", length = 128)
    private String cpid;

    @Column(name = "classification", nullable = false, length = 32)
    private String classification = "SUSPECTED";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "OPEN";

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(name = "investigated_by", length = 255)
    private String investigatedBy;

    @Column(name = "findings")
    private String findings;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getOutbreakId() { return outbreakId; }
    public void setOutbreakId(UUID outbreakId) { this.outbreakId = outbreakId; }
    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }
    public UUID getSiteId() { return siteId; }
    public void setSiteId(UUID siteId) { this.siteId = siteId; }
    public String getCpid() { return cpid; }
    public void setCpid(String cpid) { this.cpid = cpid; }
    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public void setOnsetDate(LocalDate onsetDate) { this.onsetDate = onsetDate; }
    public String getInvestigatedBy() { return investigatedBy; }
    public void setInvestigatedBy(String investigatedBy) { this.investigatedBy = investigatedBy; }
    public String getFindings() { return findings; }
    public void setFindings(String findings) { this.findings = findings; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
