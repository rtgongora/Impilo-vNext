package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_case_party", schema = "rito")
public class CasePartyEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "party_role", nullable = false, length = 48)
    private String partyRole;

    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Column(name = "actor_type", length = 48)
    private String actorType;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_json", columnDefinition = "jsonb")
    private String contactJson;

    @Column(name = "identity_protected", nullable = false)
    private Boolean identityProtected;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPartyRole() { return partyRole; }
    public void setPartyRole(String partyRole) { this.partyRole = partyRole; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getContactJson() { return contactJson; }
    public void setContactJson(String contactJson) { this.contactJson = contactJson; }
    public Boolean getIdentityProtected() { return identityProtected; }
    public void setIdentityProtected(Boolean identityProtected) { this.identityProtected = identityProtected; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
