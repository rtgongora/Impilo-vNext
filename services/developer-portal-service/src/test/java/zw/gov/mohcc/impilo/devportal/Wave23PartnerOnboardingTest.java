package zw.gov.mohcc.impilo.devportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 23 tests: Partner onboarding lifecycle — registration, key issuance,
 * sandbox configuration, deprecation posture, and key revocation.
 */
@DisplayName("Wave 23 — Partner Onboarding Lifecycle")
class Wave23PartnerOnboardingTest {

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
    @DisplayName("Partner Registration")
    class Registration {

        @Test
        @DisplayName("Register partner with sandbox enabled")
        void registerWithSandbox() {
            var request = new RegisterClientRequest("MedTech Partner", "EHR integration",
                    "dev@medtech.co.zw", true);

            Map<String, Object> result = service.registerClient(TENANT_ID, "corr-001", "idem-001", request);

            assertThat(result.get("client_name")).isEqualTo("MedTech Partner");
            assertThat(result.get("sandbox_enabled")).isEqualTo(true);
            assertThat(result.get("status")).isEqualTo("ACTIVE");

            verify(outboxRepo).save(argThat(e ->
                    "impilo.developer-portal.client.registered.v1".equals(e.getEventType())));
        }

        @Test
        @DisplayName("Register partner without sandbox (production-only)")
        void registerWithoutSandbox() {
            var request = new RegisterClientRequest("NatPharm", "Supply chain integration",
                    "it@natpharm.co.zw", false);

            Map<String, Object> result = service.registerClient(TENANT_ID, "corr-002", "idem-002", request);

            assertThat(result.get("sandbox_enabled")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("API Key Lifecycle")
    class KeyLifecycle {

        @Test
        @DisplayName("Issue key returns imp_ prefixed raw key")
        void issueKeyFormat() {
            when(clientRepo.findById(any())).thenReturn(Optional.of(createClient()));

            UUID clientId = UUID.randomUUID();
            var request = new IssueKeyRequest("Integration Key", List.of("read:patients"), 365);

            Map<String, Object> result = service.issueApiKey(clientId, TENANT_ID, "corr-003", "idem-003", request);

            assertThat(result.get("api_key").toString()).startsWith("imp_");
            assertThat(result.get("api_key").toString()).hasSize(36); // imp_ + 32 chars
            assertThat(result.get("label")).isEqualTo("Integration Key");
        }

        @Test
        @DisplayName("Rotate key revokes old and issues new")
        void rotateKeyLifecycle() {
            UUID oldKeyId = UUID.randomUUID();
            ApiKeyEntity oldKey = new ApiKeyEntity();
            oldKey.setClientId(UUID.randomUUID());
            oldKey.setKeyPrefix("imp_old1");
            oldKey.setKeyHash("oldhash");
            oldKey.setLabel("Old Key");
            when(keyRepo.findById(oldKeyId)).thenReturn(Optional.of(oldKey));

            var request = new RotateKeyRequest("Rotated Key", 90);
            Map<String, Object> result = service.rotateApiKey(oldKeyId, TENANT_ID, "corr-004", "idem-004", request);

            assertThat(result.get("api_key").toString()).startsWith("imp_");
            assertThat(result.get("rotated_from")).isEqualTo(oldKeyId);

            verify(outboxRepo).save(argThat(e ->
                    "impilo.developer-portal.apikey.rotated.v1".equals(e.getEventType())));
        }

        @Test
        @DisplayName("Revoke key sets status to REVOKED")
        void revokeKey() {
            UUID keyId = UUID.randomUUID();
            ApiKeyEntity key = new ApiKeyEntity();
            key.setClientId(UUID.randomUUID());
            key.setKeyPrefix("imp_rev1");
            key.setKeyHash("hash");
            when(keyRepo.findById(keyId)).thenReturn(Optional.of(key));

            Map<String, Object> result = service.revokeApiKey(keyId);

            assertThat(result.get("status")).isEqualTo("REVOKED");
            verify(keyRepo).save(argThat(k -> "REVOKED".equals(k.getStatus())));
        }

        @Test
        @DisplayName("Revoke non-existent key throws")
        void revokeNonExistentKey() {
            UUID keyId = UUID.randomUUID();
            when(keyRepo.findById(keyId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revokeApiKey(keyId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("Sandbox Configuration")
    class SandboxConfig {

        @Test
        @DisplayName("Configure sandbox enables sandbox and stores config")
        void configureSandbox() {
            UUID clientId = UUID.randomUUID();
            ClientEntity client = createClient();
            when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));

            Map<String, Object> result = service.configureSandbox(
                    clientId, TENANT_ID, "corr-005",
                    "{\"rate_limit\":100,\"mock_fhir\":true}");

            assertThat(result.get("sandbox_enabled")).isEqualTo(true);
            assertThat(result.get("sandbox_config")).isEqualTo("{\"rate_limit\":100,\"mock_fhir\":true}");
            verify(clientRepo).save(argThat(c -> c.isSandboxEnabled()));
        }

        @Test
        @DisplayName("Configure sandbox for non-existent client throws")
        void configureNonExistentClient() {
            UUID clientId = UUID.randomUUID();
            when(clientRepo.findById(clientId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.configureSandbox(
                    clientId, TENANT_ID, "corr-006", "{}"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Deprecation Posture")
    class DeprecationPosture {

        @Test
        @DisplayName("Set deprecation posture to WARN")
        void setWarn() {
            UUID clientId = UUID.randomUUID();
            when(clientRepo.findById(clientId)).thenReturn(Optional.of(createClient()));

            Map<String, Object> result = service.updateDeprecationPosture(clientId, "WARN");

            assertThat(result.get("deprecation_posture")).isEqualTo("WARN");
        }

        @Test
        @DisplayName("Set deprecation posture to BLOCK")
        void setBlock() {
            UUID clientId = UUID.randomUUID();
            when(clientRepo.findById(clientId)).thenReturn(Optional.of(createClient()));

            Map<String, Object> result = service.updateDeprecationPosture(clientId, "BLOCK");

            assertThat(result.get("deprecation_posture")).isEqualTo("BLOCK");
        }

        @Test
        @DisplayName("Invalid deprecation posture rejected")
        void invalidPosture() {
            UUID clientId = UUID.randomUUID();
            when(clientRepo.findById(clientId)).thenReturn(Optional.of(createClient()));

            assertThatThrownBy(() -> service.updateDeprecationPosture(clientId, "INVALID"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INVALID");
        }
    }

    @Nested
    @DisplayName("Audit Trail")
    class AuditTrail {

        @Test
        @DisplayName("Registration emits outbox event with client details")
        void registrationAudit() {
            var request = new RegisterClientRequest("Audit Test", "test", "a@b.com", false);

            service.registerClient(TENANT_ID, "corr-audit", "idem-audit", request);

            verify(outboxRepo).save(argThat(e -> {
                boolean correctType = "impilo.developer-portal.client.registered.v1".equals(e.getEventType());
                boolean hasPayload = e.getPayloadJson() != null && e.getPayloadJson().contains("Audit Test");
                boolean correctIdempotency = "idem-audit".equals(e.getIdempotencyKey());
                return correctType && hasPayload && correctIdempotency;
            }));
        }

        @Test
        @DisplayName("Key issuance emits outbox event with key prefix")
        void keyIssuanceAudit() {
            when(clientRepo.findById(any())).thenReturn(Optional.of(createClient()));

            service.issueApiKey(UUID.randomUUID(), TENANT_ID, "corr-audit2", "idem-audit2",
                    new IssueKeyRequest("Audit Key", null, null));

            verify(outboxRepo).save(argThat(e -> {
                boolean correctType = "impilo.developer-portal.apikey.issued.v1".equals(e.getEventType());
                boolean hasPrefix = e.getPayloadJson() != null && e.getPayloadJson().contains("key_prefix");
                return correctType && hasPrefix;
            }));
        }
    }

    private ClientEntity createClient() {
        ClientEntity c = new ClientEntity();
        c.setTenantId(TENANT_ID);
        c.setClientName("Test Partner");
        c.setContactEmail("test@partner.com");
        return c;
    }
}
