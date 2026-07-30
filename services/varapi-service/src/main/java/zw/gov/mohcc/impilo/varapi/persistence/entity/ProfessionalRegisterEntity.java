package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A statutory professional register within a council (ROM-W3, R4). */
@Entity
@Table(name = "professional_registers", schema = "varapi")
public class ProfessionalRegisterEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "council_id", nullable = false) private Long councilId;
    @Column(name = "register_code", nullable = false, length = 64) private String registerCode;
    @Column(name = "name", nullable = false, length = 255) private String name;
    @Column(name = "description", columnDefinition = "TEXT") private String description;
    @Column(name = "status", nullable = false, length = 20) private String status = "ACTIVE";

    // Added by V039 — which axis the register measures, and its title in law.
    @Column(name = "register_axis", length = 24) private String registerAxis;
    @Column(name = "statutory_title", length = 160) private String statutoryTitle;
    @Column(name = "statutory_title_decision_ref", length = 64) private String statutoryTitleDecisionRef;

    // Added by V043 — where this register came from, and when it stopped being offered.
    @Column(name = "source", nullable = false, length = 24) private String source = "MIGRATION_SEED";
    @Column(name = "config_definition_key", length = 128) private String configDefinitionKey;
    @Column(name = "config_semantic_version", length = 32) private String configSemanticVersion;
    @Column(name = "config_content_hash", length = 128) private String configContentHash;
    @Column(name = "materialised_at") private Instant materialisedAt;
    @Column(name = "retired_at") private Instant retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID t) { this.tenantId = t; }
    public Long getCouncilId() { return councilId; } public void setCouncilId(Long c) { this.councilId = c; }
    public String getRegisterCode() { return registerCode; } public void setRegisterCode(String c) { this.registerCode = c; }
    public String getName() { return name; } public void setName(String n) { this.name = n; }
    public String getDescription() { return description; } public void setDescription(String d) { this.description = d; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }

    public String getRegisterAxis() { return registerAxis; }
    public void setRegisterAxis(String a) { this.registerAxis = a; }

    public String getStatutoryTitle() { return statutoryTitle; }
    public void setStatutoryTitle(String t) { this.statutoryTitle = t; }

    public String getStatutoryTitleDecisionRef() { return statutoryTitleDecisionRef; }
    public void setStatutoryTitleDecisionRef(String r) { this.statutoryTitleDecisionRef = r; }

    public String getSource() { return source; } public void setSource(String s) { this.source = s; }

    public String getConfigDefinitionKey() { return configDefinitionKey; }
    public void setConfigDefinitionKey(String k) { this.configDefinitionKey = k; }

    public String getConfigSemanticVersion() { return configSemanticVersion; }
    public void setConfigSemanticVersion(String v) { this.configSemanticVersion = v; }

    public String getConfigContentHash() { return configContentHash; }
    public void setConfigContentHash(String h) { this.configContentHash = h; }

    public Instant getMaterialisedAt() { return materialisedAt; }
    public void setMaterialisedAt(Instant t) { this.materialisedAt = t; }

    public Instant getRetiredAt() { return retiredAt; }
    public void setRetiredAt(Instant t) { this.retiredAt = t; }
}
