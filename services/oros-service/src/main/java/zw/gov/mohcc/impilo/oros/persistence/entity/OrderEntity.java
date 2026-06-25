package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;

/**
 * Represents a clinical order (lab, imaging, pharmacy, procedure, etc.).
 * The orderId is a ULID string assigned by the application layer, not auto-generated.
 */
@Entity
@Table(name = "oros_orders")
public class OrderEntity {

    @Id
    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "patient_cpid", nullable = false)
    private String patientCpid;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private OrderPriority priority = OrderPriority.ROUTINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(name = "placed_by", nullable = false)
    private String placedBy;

    @Column(name = "placed_at")
    private OffsetDateTime placedAt;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "encounter_ref")
    private String encounterRef;

    @Column(name = "zibo_order_code")
    private String ziboOrderCode;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "external_refs", columnDefinition = "jsonb")
    private String externalRefs;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "butano_refs", columnDefinition = "jsonb")
    private String butanoRefs;

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    // ── Diagnostic/imaging journey (V003) ────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "request_source", nullable = false)
    private RequestSource requestSource = RequestSource.INTERNAL;

    @Column(name = "accession_number", length = 64)
    private String accessionNumber;

    @Column(name = "referring_provider_id", length = 64)
    private String referringProviderId;

    @Column(name = "referring_provider_name", length = 255)
    private String referringProviderName;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safety_json", columnDefinition = "jsonb")
    private String safetyJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "imaging_state", length = 32)
    private ImagingWorkflowState imagingState;

    // ── Linked PACS/DICOM study (V008) ───────────────────────────────────
    @Column(name = "study_uid", length = 128)
    private String studyUid;

    @Column(name = "study_viewer_url", length = 512)
    private String studyViewerUrl;

    // ── External-origin idempotency (V010) ───────────────────────────────
    @Column(name = "external_order_ref", length = 128)
    private String externalOrderRef;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }

    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }

    public OrderPriority getPriority() { return priority; }
    public void setPriority(OrderPriority priority) { this.priority = priority; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getPlacedBy() { return placedBy; }
    public void setPlacedBy(String placedBy) { this.placedBy = placedBy; }

    public OffsetDateTime getPlacedAt() { return placedAt; }
    public void setPlacedAt(OffsetDateTime placedAt) { this.placedAt = placedAt; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public String getEncounterRef() { return encounterRef; }
    public void setEncounterRef(String encounterRef) { this.encounterRef = encounterRef; }

    public String getZiboOrderCode() { return ziboOrderCode; }
    public void setZiboOrderCode(String ziboOrderCode) { this.ziboOrderCode = ziboOrderCode; }

    public String getExternalRefs() { return externalRefs; }
    public void setExternalRefs(String externalRefs) { this.externalRefs = externalRefs; }

    public String getButanoRefs() { return butanoRefs; }
    public void setButanoRefs(String butanoRefs) { this.butanoRefs = butanoRefs; }

    public String getClinicalNotes() { return clinicalNotes; }
    public void setClinicalNotes(String clinicalNotes) { this.clinicalNotes = clinicalNotes; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public RequestSource getRequestSource() { return requestSource; }
    public void setRequestSource(RequestSource requestSource) { this.requestSource = requestSource; }

    public String getAccessionNumber() { return accessionNumber; }
    public void setAccessionNumber(String accessionNumber) { this.accessionNumber = accessionNumber; }

    public String getReferringProviderId() { return referringProviderId; }
    public void setReferringProviderId(String referringProviderId) { this.referringProviderId = referringProviderId; }

    public String getReferringProviderName() { return referringProviderName; }
    public void setReferringProviderName(String referringProviderName) { this.referringProviderName = referringProviderName; }

    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getSafetyJson() { return safetyJson; }
    public void setSafetyJson(String safetyJson) { this.safetyJson = safetyJson; }

    public ImagingWorkflowState getImagingState() { return imagingState; }
    public void setImagingState(ImagingWorkflowState imagingState) { this.imagingState = imagingState; }

    public String getStudyUid() { return studyUid; }
    public void setStudyUid(String studyUid) { this.studyUid = studyUid; }

    public String getStudyViewerUrl() { return studyViewerUrl; }
    public void setStudyViewerUrl(String studyViewerUrl) { this.studyViewerUrl = studyViewerUrl; }

    public String getExternalOrderRef() { return externalOrderRef; }
    public void setExternalOrderRef(String externalOrderRef) { this.externalOrderRef = externalOrderRef; }
}
