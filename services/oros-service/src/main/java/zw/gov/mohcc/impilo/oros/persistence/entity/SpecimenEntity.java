package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.oros.domain.SpecimenStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A laboratory specimen collected against an order (spec §7). Carries the physical
 * collection → dispatch → receipt → rejection/recollection lifecycle and an append-only
 * chain-of-custody trail.
 */
@Entity
@Table(name = "oros_specimens")
public class SpecimenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "specimen_id", nullable = false)
    private UUID specimenId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "specimen_type", length = 100)
    private String specimenType;

    @Column(name = "specimen_source", length = 100)
    private String specimenSource;

    @Column(name = "body_site", length = 100)
    private String bodySite;

    @Column(name = "sample_id", length = 64)
    private String sampleId;

    @Column(name = "lab_number", length = 64)
    private String labNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private SpecimenStatus status = SpecimenStatus.COLLECTED;

    @Column(name = "fasting_status", length = 16)
    private String fastingStatus;

    @Column(name = "collection_instructions", columnDefinition = "TEXT")
    private String collectionInstructions;

    @Column(name = "collected_by", length = 128)
    private String collectedBy;

    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "collection_site", length = 255)
    private String collectionSite;

    @Column(name = "dispatched_by", length = 128)
    private String dispatchedBy;

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "received_by", length = 128)
    private String receivedBy;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chain_of_custody", columnDefinition = "jsonb")
    private String chainOfCustody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getSpecimenId() { return specimenId; }
    public void setSpecimenId(UUID specimenId) { this.specimenId = specimenId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getSpecimenType() { return specimenType; }
    public void setSpecimenType(String specimenType) { this.specimenType = specimenType; }

    public String getSpecimenSource() { return specimenSource; }
    public void setSpecimenSource(String specimenSource) { this.specimenSource = specimenSource; }

    public String getBodySite() { return bodySite; }
    public void setBodySite(String bodySite) { this.bodySite = bodySite; }

    public String getSampleId() { return sampleId; }
    public void setSampleId(String sampleId) { this.sampleId = sampleId; }

    public String getLabNumber() { return labNumber; }
    public void setLabNumber(String labNumber) { this.labNumber = labNumber; }

    public SpecimenStatus getStatus() { return status; }
    public void setStatus(SpecimenStatus status) { this.status = status; }

    public String getFastingStatus() { return fastingStatus; }
    public void setFastingStatus(String fastingStatus) { this.fastingStatus = fastingStatus; }

    public String getCollectionInstructions() { return collectionInstructions; }
    public void setCollectionInstructions(String collectionInstructions) { this.collectionInstructions = collectionInstructions; }

    public String getCollectedBy() { return collectedBy; }
    public void setCollectedBy(String collectedBy) { this.collectedBy = collectedBy; }

    public OffsetDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(OffsetDateTime collectedAt) { this.collectedAt = collectedAt; }

    public String getCollectionSite() { return collectionSite; }
    public void setCollectionSite(String collectionSite) { this.collectionSite = collectionSite; }

    public String getDispatchedBy() { return dispatchedBy; }
    public void setDispatchedBy(String dispatchedBy) { this.dispatchedBy = dispatchedBy; }

    public OffsetDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(OffsetDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getChainOfCustody() { return chainOfCustody; }
    public void setChainOfCustody(String chainOfCustody) { this.chainOfCustody = chainOfCustody; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
