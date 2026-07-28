package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A complication profile (pipeline §18). {@code procedure_definition.complication_profile}
 * (V002) references this by code, resolved at read time — the same pattern already used for
 * {@code safety_pause_template} and {@code aftercare_template}.
 */
@Entity
@Table(name = "complication_profile", schema = "procedures")
public class ComplicationProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "profile_code", nullable = false, length = 64)
    private String profileCode;

    @Column(name = "profile_name", nullable = false)
    private String profileName;

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
    public String getProfileCode() { return profileCode; }
    public String getProfileName() { return profileName; }
    public String getApplicableSetting() { return applicableSetting; }
    public String getDescription() { return description; }
    public String getSourceCitation() { return sourceCitation; }
    public String getStatus() { return status; }
    public String getApprovingAuthority() { return approvingAuthority; }
    public String getContentMaturity() { return contentMaturity; }
}
