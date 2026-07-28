package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A specialty's operative indication (SB-2/SB-4, surgical domain pack §6) — "content over
 * shared infrastructure": the shared spine (S1-S14) is built once, specialties contribute
 * content here rather than forking their own episode model.
 */
@Entity
@Table(name = "surgical_specialty_indication", schema = "surgery")
public class SurgicalSpecialtyIndicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 24)
    private String specialty;

    @Column(name = "indication_code", nullable = false, length = 32)
    private String indicationCode;

    @Column(name = "condition_display", nullable = false, columnDefinition = "text")
    private String conditionDisplay;

    @Column(name = "typical_procedure", columnDefinition = "text")
    private String typicalProcedure;

    @Column(name = "urgency_guidance", columnDefinition = "text")
    private String urgencyGuidance;

    @Column(name = "non_operative_alternatives", columnDefinition = "text")
    private String nonOperativeAlternatives;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "content_maturity", nullable = false, length = 24)
    private String contentMaturity = "ENGINEERING_SEED";

    @Column(name = "approving_authority", nullable = false, length = 128)
    private String approvingAuthority = "PENDING_MOHCC_RATIFICATION";

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSpecialty() { return specialty; }
    public String getIndicationCode() { return indicationCode; }
    public String getConditionDisplay() { return conditionDisplay; }
    public String getTypicalProcedure() { return typicalProcedure; }
    public String getUrgencyGuidance() { return urgencyGuidance; }
    public String getNonOperativeAlternatives() { return nonOperativeAlternatives; }
    public String getNotes() { return notes; }
    public String getContentMaturity() { return contentMaturity; }
    public String getApprovingAuthority() { return approvingAuthority; }
}
