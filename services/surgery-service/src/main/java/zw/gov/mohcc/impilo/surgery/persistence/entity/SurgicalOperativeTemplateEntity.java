package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A specialty's operative template (SB-2/SB-4, surgical domain pack §6/§13-adjacent) — the
 * structured content half of "content over shared infrastructure".
 */
@Entity
@Table(name = "surgical_operative_template", schema = "surgery")
public class SurgicalOperativeTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 24)
    private String specialty;

    @Column(name = "template_code", nullable = false, length = 32)
    private String templateCode;

    @Column(name = "template_name", nullable = false, columnDefinition = "text")
    private String templateName;

    @Column(name = "typical_procedure", columnDefinition = "text")
    private String typicalProcedure;

    @Column(columnDefinition = "text")
    private String position;

    @Column(columnDefinition = "text")
    private String preparation;

    @Column(columnDefinition = "text")
    private String incision;

    @Column(name = "key_steps", columnDefinition = "text")
    private String keySteps;

    @Column(name = "technique_options", columnDefinition = "text")
    private String techniqueOptions;

    @Column(name = "closure_plan", columnDefinition = "text")
    private String closurePlan;

    @Column(name = "wound_classification", length = 24)
    private String woundClassification;

    @Column(name = "postop_instructions", columnDefinition = "text")
    private String postopInstructions;

    @Column(name = "content_maturity", nullable = false, length = 24)
    private String contentMaturity = "ENGINEERING_SEED";

    @Column(name = "approving_authority", nullable = false, length = 128)
    private String approvingAuthority = "PENDING_MOHCC_RATIFICATION";

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSpecialty() { return specialty; }
    public String getTemplateCode() { return templateCode; }
    public String getTemplateName() { return templateName; }
    public String getTypicalProcedure() { return typicalProcedure; }
    public String getPosition() { return position; }
    public String getPreparation() { return preparation; }
    public String getIncision() { return incision; }
    public String getKeySteps() { return keySteps; }
    public String getTechniqueOptions() { return techniqueOptions; }
    public String getClosurePlan() { return closurePlan; }
    public String getWoundClassification() { return woundClassification; }
    public String getPostopInstructions() { return postopInstructions; }
    public String getContentMaturity() { return contentMaturity; }
    public String getApprovingAuthority() { return approvingAuthority; }
}
