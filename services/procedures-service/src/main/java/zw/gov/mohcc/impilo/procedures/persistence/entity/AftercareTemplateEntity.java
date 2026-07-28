package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * An aftercare template (pipeline §17). {@code procedure_definition.default_aftercare_template}
 * references this by code, resolved at read time — the same pattern already used for
 * {@code safety_pause_template} and {@code consent_type}.
 */
@Entity
@Table(name = "aftercare_template", schema = "procedures")
public class AftercareTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(name = "applicable_setting", length = 48)
    private String applicableSetting;

    private String description;

    @Column(name = "source_citation")
    private String sourceCitation;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "approving_authority", length = 128)
    private String approvingAuthority;

    @Column(name = "content_maturity", nullable = false, length = 24)
    private String contentMaturity;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTemplateCode() { return templateCode; }
    public String getTemplateName() { return templateName; }
    public String getApplicableSetting() { return applicableSetting; }
    public String getDescription() { return description; }
    public String getSourceCitation() { return sourceCitation; }
    public String getStatus() { return status; }
    public String getApprovingAuthority() { return approvingAuthority; }
    public String getContentMaturity() { return contentMaturity; }
}
