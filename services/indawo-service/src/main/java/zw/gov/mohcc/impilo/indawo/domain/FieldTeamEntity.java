package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** A field / rapid-response / inspection team for public-health work (Indawo SoR). */
@Entity
@Table(name = "ind_field_teams")
public class FieldTeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "team_type", nullable = false, length = 64)
    private String teamType = "RAPID_RESPONSE";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "AVAILABLE";

    @Column(name = "lead_health_id", length = 128) // actor-plane HID
    private String leadHealthId; // actor-plane HID

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    @Column(name = "base_site_id")
    private UUID baseSiteId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getTeamId() { return teamId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTeamType() { return teamType; }
    public void setTeamType(String teamType) { this.teamType = teamType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLeadHealthId() { return leadHealthId; } // actor-plane HID
    public void setLeadHealthId(String leadHealthId) { this.leadHealthId = leadHealthId; } // actor-plane HID
    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    public UUID getBaseSiteId() { return baseSiteId; }
    public void setBaseSiteId(UUID baseSiteId) { this.baseSiteId = baseSiteId; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
