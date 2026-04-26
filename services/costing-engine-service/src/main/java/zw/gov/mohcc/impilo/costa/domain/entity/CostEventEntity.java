package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_cost_events")
public class CostEventEntity {

    @Id
    @Column(name = "cost_event_id", length = 26)
    private String costEventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_cpid", length = 80)
    private String patientCpid;

    @Column(name = "encounter_id", length = 80)
    private String encounterId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "provider_role", length = 120)
    private String providerRole;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "event_source", nullable = false, length = 120)
    private String eventSource;

    @Column(name = "service_domain", length = 120)
    private String serviceDomain;

    @Column(name = "service_category", length = 120)
    private String serviceCategory;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_of_measure", length = 40)
    private String unitOfMeasure;

    @Column(name = "linked_order_id", length = 80)
    private String linkedOrderId;

    @Column(name = "linked_procedure_id", length = 80)
    private String linkedProcedureId;

    @Column(name = "linked_charge_sheet_id", length = 26)
    private String linkedChargeSheetId;

    @Column(name = "costing_status", nullable = false, length = 40)
    private String costingStatus = "captured";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public String getCostEventId() {
        return costEventId;
    }

    public void setCostEventId(String costEventId) {
        this.costEventId = costEventId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPatientCpid() {
        return patientCpid;
    }

    public void setPatientCpid(String patientCpid) {
        this.patientCpid = patientCpid;
    }

    public String getEncounterId() {
        return encounterId;
    }

    public void setEncounterId(String encounterId) {
        this.encounterId = encounterId;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public void setProviderId(UUID providerId) {
        this.providerId = providerId;
    }

    public String getProviderRole() {
        return providerRole;
    }

    public void setProviderRole(String providerRole) {
        this.providerRole = providerRole;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventSource() {
        return eventSource;
    }

    public void setEventSource(String eventSource) {
        this.eventSource = eventSource;
    }

    public String getServiceDomain() {
        return serviceDomain;
    }

    public void setServiceDomain(String serviceDomain) {
        this.serviceDomain = serviceDomain;
    }

    public String getServiceCategory() {
        return serviceCategory;
    }

    public void setServiceCategory(String serviceCategory) {
        this.serviceCategory = serviceCategory;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public String getLinkedOrderId() {
        return linkedOrderId;
    }

    public void setLinkedOrderId(String linkedOrderId) {
        this.linkedOrderId = linkedOrderId;
    }

    public String getLinkedProcedureId() {
        return linkedProcedureId;
    }

    public void setLinkedProcedureId(String linkedProcedureId) {
        this.linkedProcedureId = linkedProcedureId;
    }

    public String getLinkedChargeSheetId() {
        return linkedChargeSheetId;
    }

    public void setLinkedChargeSheetId(String linkedChargeSheetId) {
        this.linkedChargeSheetId = linkedChargeSheetId;
    }

    public String getCostingStatus() {
        return costingStatus;
    }

    public void setCostingStatus(String costingStatus) {
        this.costingStatus = costingStatus;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
