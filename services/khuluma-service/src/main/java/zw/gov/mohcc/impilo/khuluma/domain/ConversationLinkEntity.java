package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Links a conversation to a canonical Health-OS object (patient, encounter, order,
 * referral, case…) so coordination threads attach to the record they are about.
 * Khuluma stores only the reference; the object truth stays in its owning service.
 */
@Entity
@Table(name = "khuluma_conversation_links")
public class ConversationLinkEntity {

    @Id
    @Column(name = "link_id", nullable = false)
    private UUID linkId = UUID.randomUUID();

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "object_type", nullable = false, length = 64)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 255)
    private String objectId;

    @Column(name = "link_role", length = 64)
    private String linkRole;

    @Column(name = "linked_by", nullable = false, length = 255)
    private String linkedBy;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public ConversationLinkEntity() {}

    public UUID getLinkId() { return linkId; }
    public void setLinkId(UUID linkId) { this.linkId = linkId; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public String getLinkRole() { return linkRole; }
    public void setLinkRole(String linkRole) { this.linkRole = linkRole; }

    public String getLinkedBy() { return linkedBy; }
    public void setLinkedBy(String linkedBy) { this.linkedBy = linkedBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
