package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "oros_order_set_definitions")
public class OrderSetDefinitionEntity {
    @Id @Column(name = "set_id") private UUID setId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "set_code") private String setCode;
    @Column(name = "set_name") private String setName;
    @Column(name = "indication") private String indication;
    @Column(name = "description") private String description;
    @Column(name = "active") private Boolean active;
    @Column(name = "created_at") private OffsetDateTime createdAt;
    @Column(name = "updated_at") private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (setId == null) setId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (active == null) active = true;
    }

    public UUID getSetId() { return setId; }
    public void setSetId(UUID v) { setId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public String getSetCode() { return setCode; }
    public void setSetCode(String v) { setCode = v; }
    public String getSetName() { return setName; }
    public void setSetName(String v) { setName = v; }
    public String getIndication() { return indication; }
    public void setIndication(String v) { indication = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { active = v; }
}
