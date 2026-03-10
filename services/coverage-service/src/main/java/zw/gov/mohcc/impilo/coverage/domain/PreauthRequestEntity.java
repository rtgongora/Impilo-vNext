package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_preauth_requests")
public class PreauthRequestEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "coverage_id", nullable = false)
    private UUID coverageId;

    @Column(name = "facility_id", nullable = false, length = 255)
    private String facilityId;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Column(name = "request_type", nullable = false, length = 32)
    private String requestType;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "clinical_info", columnDefinition = "TEXT")
    private String clinicalInfo;

    @Column(name = "requested_items", nullable = false, columnDefinition = "TEXT")
    private String requestedItems;

    @Column(name = "decision_json", columnDefinition = "TEXT")
    private String decisionJson;

    @Column(name = "decision_evidence_json", columnDefinition = "TEXT")
    private String decisionEvidenceJson;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PreauthRequestEntity() {}

    public PreauthRequestEntity(UUID tenantId, String podId, UUID coverageId,
                                String facilityId, String providerId,
                                String requestType, String clinicalInfo,
                                String requestedItems) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.podId = podId;
        this.coverageId = coverageId;
        this.facilityId = facilityId;
        this.providerId = providerId;
        this.requestType = requestType;
        this.clinicalInfo = clinicalInfo;
        this.requestedItems = requestedItems;
        OffsetDateTime now = OffsetDateTime.now();
        this.requestedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getCoverageId() { return coverageId; }
    public String getFacilityId() { return facilityId; }
    public String getProviderId() { return providerId; }
    public String getRequestType() { return requestType; }
    public String getStatus() { return status; }
    public String getClinicalInfo() { return clinicalInfo; }
    public String getRequestedItems() { return requestedItems; }
    public String getDecisionJson() { return decisionJson; }
    public String getDecisionEvidenceJson() { return decisionEvidenceJson; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void decide(String status, String decisionJson, String decisionEvidenceJson) {
        this.status = status;
        this.decisionJson = decisionJson;
        this.decisionEvidenceJson = decisionEvidenceJson;
        OffsetDateTime now = OffsetDateTime.now();
        this.decidedAt = now;
        this.updatedAt = now;
        if ("APPROVED".equals(status)) {
            this.expiresAt = now.plusDays(30);
        }
    }
}
