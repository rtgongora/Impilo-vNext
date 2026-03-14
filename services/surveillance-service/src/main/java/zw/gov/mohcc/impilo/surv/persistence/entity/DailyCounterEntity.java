package zw.gov.mohcc.impilo.surv.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "daily_counter", schema = "surv")
public class DailyCounterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "syndrome_code", nullable = false, length = 128)
    private String syndromeCode;

    @Column(name = "count_date", nullable = false)
    private LocalDate countDate;

    @Column(name = "event_count", nullable = false)
    private int eventCount = 0;

    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt = OffsetDateTime.now();

    public DailyCounterEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getSyndromeCode() { return syndromeCode; }
    public void setSyndromeCode(String syndromeCode) { this.syndromeCode = syndromeCode; }
    public LocalDate getCountDate() { return countDate; }
    public void setCountDate(LocalDate countDate) { this.countDate = countDate; }
    public int getEventCount() { return eventCount; }
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }
    public OffsetDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(OffsetDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}
