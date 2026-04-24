package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "lrn_helpdesk_knowledge_link")
public class HelpdeskKnowledgeLinkEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "issue_type_or_topic", nullable = false, length = 128)
    private String issueTypeOrTopic;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "path_id")
    private UUID pathId;

    @Column(name = "relation_type", nullable = false, length = 64)
    private String relationType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getIssueTypeOrTopic() {
        return issueTypeOrTopic;
    }

    public void setIssueTypeOrTopic(String issueTypeOrTopic) {
        this.issueTypeOrTopic = issueTypeOrTopic;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public UUID getPathId() {
        return pathId;
    }

    public void setPathId(UUID pathId) {
        this.pathId = pathId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
