package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A class-specific safety-pause template (pipeline §9). {@code procedure_definition
 * .safety_pause_template} references this by code, resolved at read time rather than
 * enforced by a cross-table foreign key — the same pattern already used for
 * {@code consent_type} and {@code aftercare_template}, because governed content from
 * different tables is meant to evolve independently.
 */
@Entity
@Table(name = "safety_pause_template", schema = "procedures")
public class SafetyPauseTemplateEntity {

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

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    @OrderBy("displayOrder ASC")
    private List<SafetyPauseConfirmationItemEntity> confirmationItems = new ArrayList<>();

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTemplateCode() { return templateCode; }
    public String getTemplateName() { return templateName; }
    public String getApplicableSetting() { return applicableSetting; }
    public String getDescription() { return description; }
    public String getSourceCitation() { return sourceCitation; }
    public String getStatus() { return status; }
    public String getApprovingAuthority() { return approvingAuthority; }
    public List<SafetyPauseConfirmationItemEntity> getConfirmationItems() { return confirmationItems; }
}
