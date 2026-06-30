package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Wellness coaching relationship (coach ↔ participant). Coach identity stays Varapi/Vashandi;
 * this is a wellness relationship, NOT a clinical encounter unless routed to PCT.
 */
@Entity
@Table(name = "coaching_relationships", schema = "simba")
public class CoachingRelationshipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "relationship_id", nullable = false, unique = true)
    private UUID relationshipId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "coach_provider_id", nullable = false, length = 128)
    private String coachProviderId;

    @Column(name = "coach_cadre", length = 48)
    private String coachCadre;

    @Column(name = "focus", length = 64)
    private String focus;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (relationshipId == null) {
            relationshipId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        if (startedAt == null) {
            startedAt = now;
        }
    }

    public Long getId() { return id; }
    public UUID getRelationshipId() { return relationshipId; }
    public void setRelationshipId(UUID relationshipId) { this.relationshipId = relationshipId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPersonCpid() { return personCpid; }
    public void setPersonCpid(String personCpid) { this.personCpid = personCpid; }
    public String getCoachProviderId() { return coachProviderId; }
    public void setCoachProviderId(String coachProviderId) { this.coachProviderId = coachProviderId; }
    public String getCoachCadre() { return coachCadre; }
    public void setCoachCadre(String coachCadre) { this.coachCadre = coachCadre; }
    public String getFocus() { return focus; }
    public void setFocus(String focus) { this.focus = focus; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
