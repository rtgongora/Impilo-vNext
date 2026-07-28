package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * One recovery setting on the non-theatre-inclusive recovery continuum (pipeline §15). PACU
 * already exists as a real, gated theatre concept in inpatient-service; this closes the
 * structural absence the audit found — bedside, endoscopy/dialysis-scale recovery bays and
 * extended observation had nowhere to be declared at all.
 */
@Entity
@Table(name = "recovery_setting", schema = "procedures")
public class RecoverySettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "setting_code", nullable = false, length = 32)
    private String settingCode;

    @Column(name = "setting_label", nullable = false, length = 96)
    private String settingLabel;

    @Column(name = "minimum_observation_minutes")
    private Integer minimumObservationMinutes;

    @Column(name = "discharge_readiness_criteria", nullable = false)
    private String dischargeReadinessCriteria;

    @Column(name = "monitoring_required", nullable = false)
    private String monitoringRequired;

    @Column(name = "source_citation")
    private String sourceCitation;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSettingCode() { return settingCode; }
    public String getSettingLabel() { return settingLabel; }
    public Integer getMinimumObservationMinutes() { return minimumObservationMinutes; }
    public String getDischargeReadinessCriteria() { return dischargeReadinessCriteria; }
    public String getMonitoringRequired() { return monitoringRequired; }
    public String getSourceCitation() { return sourceCitation; }
}
