package zw.gov.mohcc.impilo.tshepo.identity.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lost_device_recovery_case", schema = "tshepo_identity")
public class LostDeviceRecoveryCaseEntity {
    @Id private UUID id;
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(name="target_user_id", nullable=false) private String targetUserId;
    @Column(name="requester_user_id", nullable=false) private String requesterUserId;
    @Column(name="approver_user_id") private String approverUserId;
    @Column(nullable=false) private String status;
    @Column(nullable=false) private String reason;
    @Column(name="evidence_json", nullable=false, columnDefinition="jsonb") private String evidenceJson;
    @Column(name="selected_credential_ids", nullable=false, columnDefinition="jsonb") private String selectedCredentialIds;
    @Column(name="requested_at", nullable=false) private Instant requestedAt;
    @Column(name="approved_at") private Instant approvedAt;
    @Column(name="executed_at") private Instant executedAt;
    @Column(name="execution_reference") private String executionReference;
    @Column(name="execution_error") private String executionError;
    @Version private long version;

    public UUID getId(){return id;} public void setId(UUID v){id=v;}
    public UUID getTenantId(){return tenantId;} public void setTenantId(UUID v){tenantId=v;}
    public String getTargetUserId(){return targetUserId;} public void setTargetUserId(String v){targetUserId=v;}
    public String getRequesterUserId(){return requesterUserId;} public void setRequesterUserId(String v){requesterUserId=v;}
    public String getApproverUserId(){return approverUserId;} public void setApproverUserId(String v){approverUserId=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public String getEvidenceJson(){return evidenceJson;} public void setEvidenceJson(String v){evidenceJson=v;}
    public String getSelectedCredentialIds(){return selectedCredentialIds;} public void setSelectedCredentialIds(String v){selectedCredentialIds=v;}
    public Instant getRequestedAt(){return requestedAt;} public void setRequestedAt(Instant v){requestedAt=v;}
    public Instant getApprovedAt(){return approvedAt;} public void setApprovedAt(Instant v){approvedAt=v;}
    public Instant getExecutedAt(){return executedAt;} public void setExecutedAt(Instant v){executedAt=v;}
    public String getExecutionReference(){return executionReference;} public void setExecutionReference(String v){executionReference=v;}
    public String getExecutionError(){return executionError;} public void setExecutionError(String v){executionError=v;}
    public long getVersion(){return version;}
}
