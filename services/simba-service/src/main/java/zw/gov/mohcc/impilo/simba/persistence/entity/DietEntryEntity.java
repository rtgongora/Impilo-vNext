package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "diet_entries", schema = "simba")
public class DietEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_id", nullable = false, unique = true)
    private UUID entryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "meal_type", nullable = false, length = 16)
    private String mealType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "protein_g", precision = 8, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "carbs_g", precision = 8, scale = 2)
    private BigDecimal carbsG;

    @Column(name = "fat_g", precision = 8, scale = 2)
    private BigDecimal fatG;

    @Column(name = "fiber_g", precision = 8, scale = 2)
    private BigDecimal fiberG;

    @Column(name = "water_ml")
    private Integer waterMl;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (entryId == null) {
            entryId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPersonCpid() {
        return personCpid;
    }

    public void setPersonCpid(String personCpid) {
        this.personCpid = personCpid;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getCalories() {
        return calories;
    }

    public void setCalories(Integer calories) {
        this.calories = calories;
    }

    public BigDecimal getProteinG() {
        return proteinG;
    }

    public void setProteinG(BigDecimal proteinG) {
        this.proteinG = proteinG;
    }

    public BigDecimal getCarbsG() {
        return carbsG;
    }

    public void setCarbsG(BigDecimal carbsG) {
        this.carbsG = carbsG;
    }

    public BigDecimal getFatG() {
        return fatG;
    }

    public void setFatG(BigDecimal fatG) {
        this.fatG = fatG;
    }

    public BigDecimal getFiberG() {
        return fiberG;
    }

    public void setFiberG(BigDecimal fiberG) {
        this.fiberG = fiberG;
    }

    public Integer getWaterMl() {
        return waterMl;
    }

    public void setWaterMl(Integer waterMl) {
        this.waterMl = waterMl;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(OffsetDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
