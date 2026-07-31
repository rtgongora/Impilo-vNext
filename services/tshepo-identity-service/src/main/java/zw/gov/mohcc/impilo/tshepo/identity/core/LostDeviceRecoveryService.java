package zw.gov.mohcc.impilo.tshepo.identity.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.LostDeviceRecoveryDtos;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.LostDeviceRecoveryCaseEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.LostDeviceRecoveryCaseRepository;

@Service
public class LostDeviceRecoveryService {
    private final LostDeviceRecoveryCaseRepository repository;
    private final ObjectMapper mapper;
    public LostDeviceRecoveryService(LostDeviceRecoveryCaseRepository repository, ObjectMapper mapper) {
        this.repository=repository; this.mapper=mapper;
    }

    @Transactional
    public LostDeviceRecoveryDtos.View create(UUID tenantId, String requester,
            LostDeviceRecoveryDtos.CreateRequest request) {
        requireActor(requester);
        LostDeviceRecoveryCaseEntity entity=new LostDeviceRecoveryCaseEntity();
        entity.setId(UUID.randomUUID()); entity.setTenantId(tenantId);
        entity.setTargetUserId(request.targetUserId()); entity.setRequesterUserId(requester);
        entity.setReason(request.reason()); entity.setStatus("REQUESTED");
        entity.setEvidenceJson(write(request.identityProofEvidence()));
        entity.setSelectedCredentialIds(write(request.selectedCredentialIds().stream().distinct().toList()));
        entity.setRequestedAt(Instant.now());
        return view(repository.save(entity));
    }

    @Transactional
    public LostDeviceRecoveryDtos.View approve(UUID tenantId, UUID caseId, String approver,
            LostDeviceRecoveryDtos.ApprovalRequest request) {
        requireActor(approver); LostDeviceRecoveryCaseEntity entity=find(tenantId, caseId);
        if (!"REQUESTED".equals(entity.getStatus())) throw new IllegalStateException("Recovery case is not pending approval");
        if (approver.equals(entity.getRequesterUserId())) throw new IllegalArgumentException("Requester cannot approve their own recovery case");
        entity.setApproverUserId(approver); entity.setApprovedAt(Instant.now());
        entity.setStatus(request.approved() ? "APPROVED" : "REJECTED");
        return view(repository.save(entity));
    }

    @Transactional
    public LostDeviceRecoveryDtos.View recordExecution(UUID tenantId, UUID caseId,
            LostDeviceRecoveryDtos.ExecutionRequest request) {
        LostDeviceRecoveryCaseEntity entity=find(tenantId, caseId);
        if (!List.of("APPROVED","EXECUTION_FAILED").contains(entity.getStatus()))
            throw new IllegalStateException("Recovery case is not approved for execution");
        entity.setStatus(request.succeeded() ? "EXECUTED" : "EXECUTION_FAILED");
        entity.setExecutedAt(request.succeeded() ? Instant.now() : null);
        entity.setExecutionReference(request.executionReference());
        entity.setExecutionError(request.succeeded() ? null : request.error());
        return view(repository.save(entity));
    }

    @Transactional(readOnly=true)
    public LostDeviceRecoveryDtos.View get(UUID tenantId, UUID caseId){return view(find(tenantId,caseId));}
    private LostDeviceRecoveryCaseEntity find(UUID tenantId, UUID id){return repository.findByIdAndTenantId(id,tenantId).orElseThrow(() -> new IllegalArgumentException("Recovery case not found"));}
    private static void requireActor(String actor){if(actor==null||actor.isBlank())throw new IllegalArgumentException("Authenticated actor is required");}
    private String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid recovery evidence",e);}}
    private LostDeviceRecoveryDtos.View view(LostDeviceRecoveryCaseEntity e){try{return new LostDeviceRecoveryDtos.View(e.getId(),e.getTenantId(),e.getTargetUserId(),e.getRequesterUserId(),e.getApproverUserId(),e.getStatus(),e.getReason(),mapper.readValue(e.getEvidenceJson(),new TypeReference<Map<String,Object>>(){}),mapper.readValue(e.getSelectedCredentialIds(),new TypeReference<List<String>>(){}),e.getRequestedAt(),e.getApprovedAt(),e.getExecutedAt(),e.getExecutionReference(),e.getExecutionError());}catch(Exception ex){throw new IllegalStateException("Stored recovery case is invalid",ex);}}
}
