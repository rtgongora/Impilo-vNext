package zw.gov.mohcc.impilo.devportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.devportal.api.dto.IssueKeyRequest;
import zw.gov.mohcc.impilo.devportal.api.dto.RegisterClientRequest;
import zw.gov.mohcc.impilo.devportal.api.dto.RotateKeyRequest;
import zw.gov.mohcc.impilo.devportal.core.DeveloperPortalService;
import zw.gov.mohcc.impilo.devportal.domain.ApiKeyEntity;
import zw.gov.mohcc.impilo.devportal.domain.ClientEntity;
import zw.gov.mohcc.impilo.devportal.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.devportal.repository.ApiKeyRepository;
import zw.gov.mohcc.impilo.devportal.repository.CertificationRepository;
import zw.gov.mohcc.impilo.devportal.repository.ClientRepository;
import zw.gov.mohcc.impilo.devportal.repository.OutboxEventRepository;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeveloperPortalServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaa1111-1111-1111-1111-111111111111");

    private ClientRepository clientRepo;
    private ApiKeyRepository keyRepo;
    private CertificationRepository certRepo;
    private OutboxEventRepository outboxRepo;
    private DeveloperPortalService service;

    @BeforeEach
    void setUp() {
        clientRepo = mock(ClientRepository.class);
        keyRepo = mock(ApiKeyRepository.class);
        certRepo = mock(CertificationRepository.class);
        outboxRepo = mock(OutboxEventRepository.class);

        DeveloperPortalRepositoryMocks.stubPersistence(clientRepo, keyRepo, outboxRepo);

        service = new DeveloperPortalService(clientRepo, keyRepo, certRepo, outboxRepo, new ObjectMapper());
    }

    @Nested
    @DisplayName("Client Registration")
    class ClientRegistration {

        @Test
        @DisplayName("Register client creates entity and emits outbox event")
        void registerClient() {
            var request = new RegisterClientRequest("Test App", "A test application",
                    "dev@test.com", true);

            Map<String, Object> result = service.registerClient(TENANT_ID, "corr-001", "idem-001", request);

            assertThat(result.get("client_name")).isEqualTo("Test App");
            assertThat(result.get("status")).isEqualTo("ACTIVE");
            assertThat(result.get("sandbox_enabled")).isEqualTo(true);

            verify(clientRepo).save(argThat(c ->
                    "Test App".equals(c.getClientName()) &&
                    TENANT_ID.equals(c.getTenantId()) &&
                    c.isSandboxEnabled()));

            verify(outboxRepo).save(argThat(e ->
                    "impilo.developer-portal.client.registered.v1".equals(e.getEventType()) &&
                    "DevClient".equals(e.getAggregateType()) &&
                    "idem-001".equals(e.getIdempotencyKey())));
        }
    }

    @Nested
    @DisplayName("API Key Management")
    class ApiKeyManagement {

        @Test
        @DisplayName("Issue API key returns raw key and emits outbox event")
        void issueKey() {
            ClientEntity client = new ClientEntity();
            client.setTenantId(TENANT_ID);
            client.setClientName("Test Client");
            when(clientRepo.findById(any())).thenReturn(Optional.of(client));

            UUID clientId = UUID.randomUUID();
            var request = new IssueKeyRequest("Production Key", List.of("read", "write"), 90);

            Map<String, Object> result = service.issueApiKey(clientId, TENANT_ID, "corr-002", "idem-002", request);

            assertThat(result.get("api_key")).isNotNull();
            assertThat(result.get("api_key").toString()).startsWith("imp_");
            assertThat(result.get("key_prefix")).isNotNull();
            assertThat(result.get("status")).isEqualTo("ACTIVE");

            verify(keyRepo).save(argThat(k ->
                    k.getKeyPrefix() != null &&
                    k.getKeyHash() != null &&
                    "Production Key".equals(k.getLabel())));

            verify(outboxRepo).save(argThat(e ->
                    "impilo.developer-portal.apikey.issued.v1".equals(e.getEventType())));
        }

        @Test
        @DisplayName("Rotate API key revokes old and creates new")
        void rotateKey() {
            UUID oldKeyId = UUID.randomUUID();
            ApiKeyEntity oldKey = new ApiKeyEntity();
            oldKey.setClientId(UUID.randomUUID());
            oldKey.setKeyPrefix("imp_old1");
            oldKey.setKeyHash("oldhash");
            oldKey.setLabel("Old Key");
            when(keyRepo.findById(oldKeyId)).thenReturn(Optional.of(oldKey));

            var request = new RotateKeyRequest("Rotated Key", 30);

            Map<String, Object> result = service.rotateApiKey(oldKeyId, TENANT_ID, "corr-003", "idem-003", request);

            assertThat(result.get("api_key")).isNotNull();
            assertThat(result.get("rotated_from")).isEqualTo(oldKeyId);

            // Old key saved as ROTATED, new key saved as ACTIVE
            ArgumentCaptor<ApiKeyEntity> captor = ArgumentCaptor.forClass(ApiKeyEntity.class);
            verify(keyRepo, times(2)).save(captor.capture());
            List<ApiKeyEntity> saved = captor.getAllValues();
            assertThat(saved.get(0).getStatus()).isEqualTo("ROTATED");
            assertThat(saved.get(1).getStatus()).isEqualTo("ACTIVE");

            verify(outboxRepo).save(argThat(e ->
                    "impilo.developer-portal.apikey.rotated.v1".equals(e.getEventType())));
        }
    }
}
