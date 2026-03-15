package zw.gov.mohcc.impilo.support.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sup_ticket_comments")
public class CommentEntity {
    @Id @Column(name = "comment_id") private UUID commentId;
    @Column(name = "ticket_id", nullable = false) private UUID ticketId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "author_ref", nullable = false) private String authorRef;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    protected CommentEntity() {}
    public CommentEntity(UUID commentId, UUID ticketId, UUID tenantId, String authorRef, String body) {
        this.commentId = commentId; this.ticketId = ticketId; this.tenantId = tenantId;
        this.authorRef = authorRef; this.body = body; this.createdAt = OffsetDateTime.now();
    }

    public UUID getCommentId() { return commentId; }
    public UUID getTicketId() { return ticketId; }
    public UUID getTenantId() { return tenantId; }
    public String getAuthorRef() { return authorRef; }
    public String getBody() { return body; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
