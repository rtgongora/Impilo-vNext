package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "council_regulatory_configs", schema = "varapi")
public class CouncilRegulatoryConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "council_id", nullable = false)
    private CouncilEntity council;

    @Column(name = "config_version", nullable = false)
    private int configVersion = 1;

    @Column(name = "fees_json", columnDefinition = "JSONB")
    private String feesJson;

    @Column(name = "cpd_rules_json", columnDefinition = "JSONB")
    private String cpdRulesJson;

    @Column(name = "workflow_hints_json", columnDefinition = "JSONB")
    private String workflowHintsJson;

    @Column(name = "document_requirements_json", columnDefinition = "JSONB")
    private String documentRequirementsJson;

    @Column(name = "workflow_template_code", length = 80)
    private String workflowTemplateCode;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant n = Instant.now();
        createdAt = n;
        updatedAt = n;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public CouncilEntity getCouncil() { return council; }
    public void setCouncil(CouncilEntity council) { this.council = council; }
    public int getConfigVersion() { return configVersion; }
    public void setConfigVersion(int configVersion) { this.configVersion = configVersion; }
    public String getFeesJson() { return feesJson; }
    public void setFeesJson(String feesJson) { this.feesJson = feesJson; }
    public String getCpdRulesJson() { return cpdRulesJson; }
    public void setCpdRulesJson(String cpdRulesJson) { this.cpdRulesJson = cpdRulesJson; }
    public String getWorkflowHintsJson() { return workflowHintsJson; }
    public void setWorkflowHintsJson(String workflowHintsJson) { this.workflowHintsJson = workflowHintsJson; }
    public String getDocumentRequirementsJson() { return documentRequirementsJson; }
    public void setDocumentRequirementsJson(String documentRequirementsJson) { this.documentRequirementsJson = documentRequirementsJson; }
    public String getWorkflowTemplateCode() { return workflowTemplateCode; }
    public void setWorkflowTemplateCode(String workflowTemplateCode) { this.workflowTemplateCode = workflowTemplateCode; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
