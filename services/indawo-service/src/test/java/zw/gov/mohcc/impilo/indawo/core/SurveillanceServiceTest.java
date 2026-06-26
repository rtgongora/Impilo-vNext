package zw.gov.mohcc.impilo.indawo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.indawo.domain.*;
import zw.gov.mohcc.impilo.indawo.repository.*;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SurveillanceServiceTest {

    @Mock private SurveillanceAlertRepository alertRepository;
    @Mock private OutbreakRepository outbreakRepository;
    @Mock private CaseInvestigationRepository caseRepository;
    @Mock private FieldTeamRepository teamRepository;
    @Mock private FieldTeamDeploymentRepository deploymentRepository;
    @Mock private OutboxEventRepository outboxRepository;

    private SurveillanceService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SurveillanceService(alertRepository, outbreakRepository, caseRepository,
                teamRepository, deploymentRepository, outboxRepository, new ObjectMapper());
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outbreakRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(caseRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(teamRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(deploymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createOutbreak_emitsOutboxEvent() {
        OutbreakEntity o = new OutbreakEntity();
        o.setConditionCode("CHOLERA");
        o.setName("Budiriro cluster");
        service.createOutbreak(tenantId, "actor-1", o);

        ArgumentCaptor<OutboxEventEntity> cap = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(cap.capture());
        assertEquals("OUTBREAK_DECLARED", cap.getValue().getEventType());
        assertEquals(tenantId, cap.getValue().getTenantId());
    }

    @Test
    void deployTeam_flipsTeamToDeployed() {
        UUID teamId = UUID.randomUUID();
        FieldTeamEntity team = new FieldTeamEntity();
        team.setTenantId(tenantId);
        team.setStatus("AVAILABLE");
        when(teamRepository.findByTenantIdAndTeamId(tenantId, teamId)).thenReturn(Optional.of(team));

        service.deployTeam(tenantId, "actor-1", teamId, UUID.randomUUID(), null, "Active case finding");

        ArgumentCaptor<FieldTeamEntity> cap = ArgumentCaptor.forClass(FieldTeamEntity.class);
        verify(teamRepository).save(cap.capture());
        assertEquals("DEPLOYED", cap.getValue().getStatus());
    }

    @Test
    void recallDeployment_restoresTeamAvailability() {
        UUID depId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        FieldTeamDeploymentEntity dep = new FieldTeamDeploymentEntity();
        dep.setTenantId(tenantId);
        dep.setTeamId(teamId);
        dep.setStatus("ACTIVE");
        when(deploymentRepository.findByTenantIdAndDeploymentId(tenantId, depId)).thenReturn(Optional.of(dep));
        FieldTeamEntity team = new FieldTeamEntity();
        team.setStatus("DEPLOYED");
        when(teamRepository.findByTenantIdAndTeamId(tenantId, teamId)).thenReturn(Optional.of(team));

        FieldTeamDeploymentEntity result = service.recallDeployment(tenantId, depId, "actor-1");
        assertEquals("RECALLED", result.getStatus());
        assertNotNull(result.getRecalledAt());
        assertEquals("AVAILABLE", team.getStatus());
    }

    @Test
    void createCase_recountsParentOutbreak() {
        UUID outbreakId = UUID.randomUUID();
        CaseInvestigationEntity c = new CaseInvestigationEntity();
        c.setOutbreakId(outbreakId);
        OutbreakEntity parent = new OutbreakEntity();
        when(outbreakRepository.findByTenantIdAndOutbreakId(tenantId, outbreakId)).thenReturn(Optional.of(parent));
        when(caseRepository.countByTenantIdAndOutbreakId(tenantId, parent.getOutbreakId())).thenReturn(4L);

        service.createCase(tenantId, "actor-1", c);
        assertEquals(4, parent.getCaseCount());
    }
}
