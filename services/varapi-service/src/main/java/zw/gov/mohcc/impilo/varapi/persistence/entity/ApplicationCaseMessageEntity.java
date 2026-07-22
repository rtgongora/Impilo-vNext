package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A case-message on a provider application, APPLICANT- or INTERNAL-visible (ROM-W4, R7). */
@Entity
@Table(name = "application_case_message", schema = "varapi")
public class ApplicationCaseMessageEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "application_id", nullable = false) private Long applicationId;
    @Column(name = "direction", nullable = false, length = 12) private String direction;
    @Column(name = "author_actor_id", length = 128) private String authorActorId;
    @Column(name = "author_capacity", length = 48) private String authorCapacity;
    @Column(name = "visibility", nullable = false, length = 12) private String visibility = "APPLICANT";
    @Column(name = "body", nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void oc() { createdAt = Instant.now(); }
    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID t) { this.tenantId = t; }
    public Long getApplicationId() { return applicationId; } public void setApplicationId(Long a) { this.applicationId = a; }
    public String getDirection() { return direction; } public void setDirection(String v) { this.direction = v; }
    public String getAuthorActorId() { return authorActorId; } public void setAuthorActorId(String v) { this.authorActorId = v; }
    public String getAuthorCapacity() { return authorCapacity; } public void setAuthorCapacity(String v) { this.authorCapacity = v; }
    public String getVisibility() { return visibility; } public void setVisibility(String v) { this.visibility = v; }
    public String getBody() { return body; } public void setBody(String v) { this.body = v; }
    public Instant getCreatedAt() { return createdAt; }
}
