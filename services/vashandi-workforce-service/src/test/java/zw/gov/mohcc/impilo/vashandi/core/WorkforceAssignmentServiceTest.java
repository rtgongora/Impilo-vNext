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

import java.time.LocalDate;
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
    ReferenceValidationService referenceValidationService;
    @Mock
    VashandiOutboxWriter outboxWriter;

    WorkforceAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new WorkforceAssignmentService(assignmentRepository, eligibilityService, referenceValidationService, outboxWriter);
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

    @Test
    void create_persistsEngagementType() throws Exception {
        // Identity-program (D-P7): an operator-created posting carries its engagement
        // type through to the saved assignment (a non-PERMANENT engagement is end-dated).
        UUID tenant = UUID.randomUUID();
        when(assignmentRepository.save(any())).thenAnswer(inv -> {
            WorkforceAssignmentEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID()); // mirror DB-generated id so emit() can key on it
            }
            return e;
        });

        VashandiDtos.CreateAssignmentRequest request = new VashandiDtos.CreateAssignmentRequest(
                UUID.randomUUID(), "facility_access", UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, null, "role.template", null,
                LocalDate.now(), LocalDate.now().plusMonths(3), "operator", "LOCUM");

        WorkforceAssignmentEntity saved = service.create(tenant, request);

        assertThat(saved.getEngagementType()).isEqualTo("LOCUM");
        assertThat(saved.getStatus()).isEqualTo("requested");
        assertThat(saved.getEndDate()).isNotNull();
    }
}
