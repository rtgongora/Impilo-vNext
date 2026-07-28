package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Waiting-list clinical revalidation state (SB-2, surgical domain pack §9) — surgery-service's
 * ONLY slice of the 19-field waiting-list model; the rest stays in
 * {@code scheduling.surgical_waitlist_entry}, untouched. Append-only: each revalidation is an
 * audited event, not a mutable status.
 */
@Entity
@Table(name = "surgical_waitlist_revalidation", schema = "surgery")
public class SurgicalWaitlistRevalidationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "surgical_episode_id", nullable = false)
    private UUID surgicalEpisodeId;

    @Column(name = "revalidated_at", nullable = false)
    private OffsetDateTime revalidatedAt;

    @Column(name = "revalidated_by", nullable = false)
    private String revalidatedBy;

    @Column(name = "still_clinically_appropriate", nullable = false)
    private boolean stillClinicallyAppropriate;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "next_revalidation_due")
    private LocalDate nextRevalidationDue;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (revalidatedAt == null) revalidatedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getSurgicalEpisodeId() { return surgicalEpisodeId; }
    public void setSurgicalEpisodeId(UUID v) { this.surgicalEpisodeId = v; }
    public OffsetDateTime getRevalidatedAt() { return revalidatedAt; }
    public void setRevalidatedAt(OffsetDateTime v) { this.revalidatedAt = v; }
    public String getRevalidatedBy() { return revalidatedBy; }
    public void setRevalidatedBy(String v) { this.revalidatedBy = v; }
    public boolean isStillClinicallyAppropriate() { return stillClinicallyAppropriate; }
    public void setStillClinicallyAppropriate(boolean v) { this.stillClinicallyAppropriate = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public LocalDate getNextRevalidationDue() { return nextRevalidationDue; }
    public void setNextRevalidationDue(LocalDate v) { this.nextRevalidationDue = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
