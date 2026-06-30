package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Governed wellness plan / journey template a person can enroll into. */
@Entity
@Table(name = "wellness_plans", schema = "simba")
public class WellnessPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false, unique = true)
    private UUID planId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false, length = 64)
    private String planCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "category", nullable = false, length = 48)
    private String category;

    @Column(name = "audience", length = 48)
    private String audience = "INDIVIDUAL";

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "eligibility_min_age")
    private Integer eligibilityMinAge;

    @Column(name = "eligibility_max_age")
    private Integer eligibilityMaxAge;

    @Column(name = "fundo_content_ref", length = 128)
    private String fundoContentRef;

    @Column(name = "clinical_caution", nullable = false)
    private boolean clinicalCaution = false;

    @Column(name = "status", length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (planId == null) {
            planId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Integer getEligibilityMinAge() { return eligibilityMinAge; }
    public void setEligibilityMinAge(Integer eligibilityMinAge) { this.eligibilityMinAge = eligibilityMinAge; }
    public Integer getEligibilityMaxAge() { return eligibilityMaxAge; }
    public void setEligibilityMaxAge(Integer eligibilityMaxAge) { this.eligibilityMaxAge = eligibilityMaxAge; }
    public String getFundoContentRef() { return fundoContentRef; }
    public void setFundoContentRef(String fundoContentRef) { this.fundoContentRef = fundoContentRef; }
    public boolean isClinicalCaution() { return clinicalCaution; }
    public void setClinicalCaution(boolean clinicalCaution) { this.clinicalCaution = clinicalCaution; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
