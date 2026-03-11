package zw.gov.mohcc.impilo.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dsp_dispatch_jobs")
public class DispatchJobEntity {

    @Id
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_ref", nullable = false, length = 255)
    private String facilityRef;

    @Column(name = "request_ref", length = 255)
    private String requestRef;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "NEW";

    @Column(name = "assigned_agent_ref", length = 255)
    private String assignedAgentRef;

    @Column(name = "assigned_vehicle_ref", length = 255)
    private String assignedVehicleRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DispatchJobEntity() {}

    public DispatchJobEntity(UUID jobId, UUID tenantId, String facilityRef) {
        this.jobId = jobId;
        this.tenantId = tenantId;
        this.facilityRef = facilityRef;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getJobId() { return jobId; }
    public UUID getTenantId() { return tenantId; }
    public String getFacilityRef() { return facilityRef; }
    public void setFacilityRef(String facilityRef) { this.facilityRef = facilityRef; }
    public String getRequestRef() { return requestRef; }
    public void setRequestRef(String requestRef) { this.requestRef = requestRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssignedAgentRef() { return assignedAgentRef; }
    public void setAssignedAgentRef(String assignedAgentRef) { this.assignedAgentRef = assignedAgentRef; }
    public String getAssignedVehicleRef() { return assignedVehicleRef; }
    public void setAssignedVehicleRef(String assignedVehicleRef) { this.assignedVehicleRef = assignedVehicleRef; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
