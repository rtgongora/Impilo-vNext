package zw.gov.mohcc.impilo.dags.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.dags.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.dags.persistence.entity.PolicyEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.dags.persistence.repository.PolicyRepository;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private AuditService auditService;

    private PolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(policyRepository, outboxRepository, auditService);
    }

    @Nested
    @DisplayName("Create Policy")
    class CreatePolicy {

        @Test
        void createsAndSavesPolicy() {
            UUID tenantId = UUID.randomUUID();
            String actorId = "admin-1";
            UUID correlationId = UUID.randomUUID();

            PolicyEntity saved = new PolicyEntity();
            saved.setId(1L);
            saved.setTenantId(tenantId);
            saved.setName("PHI Access");
            saved.setResourceType("PATIENT_RECORD");
            saved.setEffect(PolicyEffect.ALLOW);
            saved.setCreatedBy(actorId);

            when(policyRepository.save(any(PolicyEntity.class))).thenReturn(saved);

            PolicyEntity result = policyService.createPolicy(
                    tenantId, actorId, correlationId,
                    "PHI Access", "Access to PHI data",
                    "PATIENT_RECORD", "{\"role\":\"doctor\"}",
                    PolicyEffect.ALLOW);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("PHI Access");
            assertThat(result.getResourceType()).isEqualTo("PATIENT_RECORD");
            assertThat(result.getEffect()).isEqualTo(PolicyEffect.ALLOW);
        }

        @Test
        void publishesOutboxEventOnCreate() {
            UUID tenantId = UUID.randomUUID();
            PolicyEntity saved = new PolicyEntity();
            saved.setId(1L);
            saved.setTenantId(tenantId);
            saved.setName("Test");
            saved.setResourceType("LAB_RESULT");
            saved.setEffect(PolicyEffect.DENY);
            saved.setCreatedBy("admin");
            saved.setStatus(PolicyStatus.ACTIVE);

            when(policyRepository.save(any(PolicyEntity.class))).thenReturn(saved);

            policyService.createPolicy(tenantId, "admin", UUID.randomUUID(),
                    "Test", null, "LAB_RESULT", null, PolicyEffect.DENY);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());

            EventOutboxEntity event = captor.getValue();
            assertThat(event.getAggregateType()).isEqualTo("POLICY");
            assertThat(event.getEventType()).isEqualTo("POLICY_CREATED");
            assertThat(event.getAggregateId()).isEqualTo("1");
        }

        @Test
        void logsAuditEntryOnCreate() {
            UUID tenantId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();
            PolicyEntity saved = new PolicyEntity();
            saved.setId(2L);
            saved.setTenantId(tenantId);
            saved.setName("Audit Test");
            saved.setResourceType("IMAGING");
            saved.setEffect(PolicyEffect.ALLOW);
            saved.setCreatedBy("admin");

            when(policyRepository.save(any(PolicyEntity.class))).thenReturn(saved);

            policyService.createPolicy(tenantId, "admin", correlationId,
                    "Audit Test", null, "IMAGING", null, PolicyEffect.ALLOW);

            verify(auditService).log(tenantId, "POLICY_CREATED", "admin",
                    "POLICY", "2", any(), correlationId);
        }

        @Test
        void defaultsEffectToDenyWhenNull() {
            UUID tenantId = UUID.randomUUID();

            ArgumentCaptor<PolicyEntity> captor = ArgumentCaptor.forClass(PolicyEntity.class);
            PolicyEntity saved = new PolicyEntity();
            saved.setId(3L);
            saved.setTenantId(tenantId);
            saved.setName("Default");
            saved.setResourceType("DATA");
            saved.setEffect(PolicyEffect.DENY);
            saved.setCreatedBy("admin");

            when(policyRepository.save(captor.capture())).thenReturn(saved);

            policyService.createPolicy(tenantId, "admin", UUID.randomUUID(),
                    "Default", null, "DATA", null, null);

            assertThat(captor.getValue().getEffect()).isEqualTo(PolicyEffect.DENY);
        }
    }

    @Nested
    @DisplayName("List Policies")
    class ListPolicies {

        @Test
        void returnsAllPoliciesForTenant() {
            UUID tenantId = UUID.randomUUID();
            PolicyEntity p1 = new PolicyEntity();
            p1.setId(1L);
            PolicyEntity p2 = new PolicyEntity();
            p2.setId(2L);

            when(policyRepository.findByTenantId(tenantId)).thenReturn(List.of(p1, p2));

            List<PolicyEntity> result = policyService.listPolicies(tenantId);
            assertThat(result).hasSize(2);
        }
    }
}
