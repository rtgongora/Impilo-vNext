package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Audit of a resolve-blocker action routed to the owning service (Wave 2). */
@Entity
@Table(name = "procedure_blocker_resolution", schema = "inpatient")
public class ProcedureBlockerResolutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "domain", nullable = false, length = 24)
    private String domain;

    @Column(name = "action", nullable = false, length = 48)
    private String action;

    @Column(name = "routed_owner", nullable = false, length = 32)
    private String routedOwner;

    @Column(name = "owner_ref", length = 256)
    private String ownerRef;

    @Column(name = "note")
    private String note;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public void setDomain(String domain) { this.domain = domain; }
    public void setAction(String action) { this.action = action; }
    public void setRoutedOwner(String routedOwner) { this.routedOwner = routedOwner; }
    public void setOwnerRef(String ownerRef) { this.ownerRef = ownerRef; }
    public void setNote(String note) { this.note = note; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getDomain() { return domain; }
    public String getAction() { return action; }
    public String getRoutedOwner() { return routedOwner; }
}
