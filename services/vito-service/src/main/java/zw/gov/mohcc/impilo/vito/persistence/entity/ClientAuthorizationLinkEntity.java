package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_authorization_link", schema = "vito")
public class ClientAuthorizationLinkEntity {

    @Id
    @Column(name = "authorization_link_id", nullable = false, updatable = false)
    private UUID authorizationLinkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_health_id", nullable = false)
    private UUID clientHealthId;

    @Column(name = "authorisation_type", nullable = false)
    private String authorisationType;

    @Column(name = "reference_id", nullable = false)
    private String referenceId;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "start_date")
    private OffsetDateTime startDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (authorizationLinkId == null) {
            authorizationLinkId = UUID.randomUUID();
        }
        if (startDate == null) {
            startDate = OffsetDateTime.now();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getAuthorizationLinkId() { return authorizationLinkId; }
    public void setAuthorizationLinkId(UUID authorizationLinkId) { this.authorizationLinkId = authorizationLinkId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getClientHealthId() { return clientHealthId; }
    public void setClientHealthId(UUID clientHealthId) { this.clientHealthId = clientHealthId; }
    public String getAuthorisationType() { return authorisationType; }
    public void setAuthorisationType(String authorisationType) { this.authorisationType = authorisationType; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getStartDate() { return startDate; }
    public void setStartDate(OffsetDateTime startDate) { this.startDate = startDate; }
    public OffsetDateTime getEndDate() { return endDate; }
    public void setEndDate(OffsetDateTime endDate) { this.endDate = endDate; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
