package zw.gov.mohcc.impilo.secharden.core;

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
import zw.gov.mohcc.impilo.secharden.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.secharden.persistence.entity.PolicyPackEntity;
import zw.gov.mohcc.impilo.secharden.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.secharden.persistence.repository.PolicyPackRepository;

@ExtendWith(MockitoExtension.class)
class PolicyPackServiceTest {

    @Mock private PolicyPackRepository policyPackRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private PolicyPackService policyPackService;

    @BeforeEach
    void setUp() {
        policyPackService = new PolicyPackService(policyPackRepository, outboxRepository);
    }

    @Nested
    @DisplayName("Create Policy Pack")
    class CreatePolicyPack {

        @Test
        void createsAndSavesPolicyPack() {
            UUID tenantId = UUID.randomUUID();
            PolicyPackEntity saved = new PolicyPackEntity();
            saved.setId(1L);
            saved.setTenantId(tenantId);
            saved.setName("Baseline Pack");
            saved.setPackType(PackType.BASELINE);
            saved.setCreatedBy("admin");

            when(policyPackRepository.save(any(PolicyPackEntity.class))).thenReturn(saved);

            PolicyPackEntity result = policyPackService.createPolicyPack(
                    tenantId, "admin", UUID.randomUUID(),
                    "Baseline Pack", "Standard baseline",
                    PackType.BASELINE, "[]");

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Baseline Pack");
        }

        @Test
        void publishesOutboxEvent() {
            UUID tenantId = UUID.randomUUID();
            PolicyPackEntity saved = new PolicyPackEntity();
            saved.setId(2L);
            saved.setTenantId(tenantId);
            saved.setName("Test");
            saved.setPackType(PackType.ADVANCED);
            saved.setCreatedBy("admin");
            saved.setStatus(PackStatus.ACTIVE);

            when(policyPackRepository.save(any(PolicyPackEntity.class))).thenReturn(saved);

            policyPackService.createPolicyPack(tenantId, "admin", UUID.randomUUID(),
                    "Test", null, PackType.ADVANCED, null);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("PACK_CREATED");
        }
    }

    @Nested
    @DisplayName("List Policy Packs")
    class ListPolicyPacks {

        @Test
        void returnsAllPacksForTenant() {
            UUID tenantId = UUID.randomUUID();
            when(policyPackRepository.findByTenantId(tenantId)).thenReturn(List.of(new PolicyPackEntity()));

            List<PolicyPackEntity> result = policyPackService.listPolicyPacks(tenantId);
            assertThat(result).hasSize(1);
        }
    }
}
