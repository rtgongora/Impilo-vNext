package zw.gov.mohcc.impilo.tshepo.identity.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.LostDeviceRecoveryDtos;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.LostDeviceRecoveryCaseEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.LostDeviceRecoveryCaseRepository;

@ExtendWith(MockitoExtension.class)
class LostDeviceRecoveryServiceTest {
    @Mock LostDeviceRecoveryCaseRepository repository;
    LostDeviceRecoveryService service;
    UUID tenant=UUID.randomUUID(); UUID caseId=UUID.randomUUID();
    @BeforeEach void setUp(){service=new LostDeviceRecoveryService(repository,new ObjectMapper());lenient().when(repository.save(any())).thenAnswer(i->i.getArgument(0));}
    @Test void createsEvidenceBoundCaseWithoutAuthority(){var view=service.create(tenant,"requester",new LostDeviceRecoveryDtos.CreateRequest("target","lost device",Map.of("proofingCase","P-1"),List.of("cred-1")));assertEquals("REQUESTED",view.status());assertNull(view.approverUserId());}
    @Test void forbidsRequesterSelfApproval(){when(repository.findByIdAndTenantId(caseId,tenant)).thenReturn(Optional.of(pending()));assertThrows(IllegalArgumentException.class,()->service.approve(tenant,caseId,"requester",new LostDeviceRecoveryDtos.ApprovalRequest(true,"")));}
    @Test void secondActorCanApprove(){when(repository.findByIdAndTenantId(caseId,tenant)).thenReturn(Optional.of(pending()));assertEquals("APPROVED",service.approve(tenant,caseId,"approver",new LostDeviceRecoveryDtos.ApprovalRequest(true,"")).status());}
    @Test void cannotExecuteUnapprovedCase(){var e=pending();when(repository.findByIdAndTenantId(caseId,tenant)).thenReturn(Optional.of(e));assertThrows(IllegalStateException.class,()->service.recordExecution(tenant,caseId,new LostDeviceRecoveryDtos.ExecutionRequest(true,"x",null)));}
    private LostDeviceRecoveryCaseEntity pending(){var e=new LostDeviceRecoveryCaseEntity();e.setId(caseId);e.setTenantId(tenant);e.setTargetUserId("target");e.setRequesterUserId("requester");e.setStatus("REQUESTED");e.setReason("lost");e.setEvidenceJson("{\"proof\":\"P-1\"}");e.setSelectedCredentialIds("[\"cred-1\"]");e.setRequestedAt(Instant.now());return e;}
}
