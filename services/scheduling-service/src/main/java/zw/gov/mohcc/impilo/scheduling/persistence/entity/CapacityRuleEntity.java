package zw.gov.mohcc.impilo.scheduling.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "capacity_rules", schema = "scheduling")
public class CapacityRuleEntity {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "version", nullable = false)
    private String version = "mvp-0.1";

    @Column(name = "overbooking_factor", nullable = false)
    private BigDecimal overbookingFactor = BigDecimal.ONE;

    @Column(name = "waitlist_enabled", nullable = false)
    private boolean waitlistEnabled;

    @Column(name = "holiday_calendar", nullable = false)
    private String holidayCalendar = "NOT_CONFIGURED";

    @Column(name = "notes")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CapacityRuleEntity() {}

    public static CapacityRuleEntity defaults(UUID tenantId) {
        CapacityRuleEntity entity = new CapacityRuleEntity();
        entity.tenantId = tenantId;
        entity.notes = "Replace with persisted rules + Tshepo ABAC when scheduling-service is promoted.";
        entity.updatedAt = OffsetDateTime.now();
        return entity;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    public UUID getTenantId() { return tenantId; }
    public String getVersion() { return version; }
    public BigDecimal getOverbookingFactor() { return overbookingFactor; }
    public boolean isWaitlistEnabled() { return waitlistEnabled; }
    public String getHolidayCalendar() { return holidayCalendar; }
    public String getNotes() { return notes; }
}
