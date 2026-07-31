package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_mpdsr_finding", schema = "rito")
public class MpdsrFindingEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "delay_type", nullable = false, length = 48)
    private String delayType;

    @Column(name = "modifiable_factor", nullable = false, length = 512)
    private String modifiableFactor;

    @Column(name = "narrative", columnDefinition = "TEXT")
    private String narrative;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getReviewId() { return reviewId; }
    public void setReviewId(UUID reviewId) { this.reviewId = reviewId; }
    public String getDelayType() { return delayType; }
    public void setDelayType(String delayType) { this.delayType = delayType; }
    public String getModifiableFactor() { return modifiableFactor; }
    public void setModifiableFactor(String modifiableFactor) { this.modifiableFactor = modifiableFactor; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
