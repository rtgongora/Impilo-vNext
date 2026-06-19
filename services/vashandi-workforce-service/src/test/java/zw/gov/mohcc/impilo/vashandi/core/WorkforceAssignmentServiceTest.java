package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.integration.IntegrationCheckResult;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkforceAssignmentServiceTest {

    @Mock
    WorkforceAssignmentRepository assignmentRepository;
    @Mock
    WorkforceEligibilityService eligibilityService;
    @Mock
    VashandiOutboxWriter outboxWriter;

    WorkforceAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new WorkforceAssignmentService(assignmentRepository, eligibilityService, outboxWriter);
    }

    @Test
    void activate_deniedWhenEligibilityBlocks() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        WorkforceAssignmentEntity assignment = new WorkforceAssignmentEntity();
        assignment.setId(assignmentId);
        assignment.setTenantId(tenant);
        assignment.setWorkforceProfileId(UUID.randomUUID());
        assignment.setStatus("approved");

        when(assignmentRepository.findByTenantIdAndId(tenant, assignmentId)).thenReturn(Optional.of(assignment));
        when(eligibilityService.evaluate(assignment, "opa-1")).thenReturn(
                new VashandiDtos.WorkforceEligibilityResult(
                        assignmentId,
                        "denied",
                        "opa-1",
                        List.of(IntegrationCheckResult.degraded("varapi", "down")),
                        "worker not eligible"));

        VashandiDtos.AssignmentActionResponse response = service.activate(tenant, assignmentId, "opa-1");

        assertThat(response.actionStatus()).isEqualTo("denied");
        assertThat(response.assignmentStatus()).isEqualTo("approved");
    }

    @Test
    void activate_completesWhenEligible() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        WorkforceAssignmentEntity assignment = new WorkforceAssignmentEntity();
        assignment.setId(assignmentId);
        assignment.setTenantId(tenant);
        assignment.setWorkforceProfileId(UUID.randomUUID());
        assignment.setStatus("approved");

        when(assignmentRepository.findByTenantIdAndId(tenant, assignmentId)).thenReturn(Optional.of(assignment));
        when(eligibilityService.evaluate(assignment, "opa-2")).thenReturn(
                new VashandiDtos.WorkforceEligibilityResult(
                        assignmentId, "allowed", "opa-2", List.of(), "eligible"));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VashandiDtos.AssignmentActionResponse response = service.activate(tenant, assignmentId, "opa-2");

        assertThat(response.actionStatus()).isEqualTo("completed");
        assertThat(response.assignmentStatus()).isEqualTo("active");
    }
}
