package zw.gov.mohcc.impilo.devportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.devportal.api.dto.IssueKeyRequest;
import zw.gov.mohcc.impilo.devportal.api.dto.RegisterClientRequest;
import zw.gov.mohcc.impilo.devportal.core.DeveloperPortalService;
import zw.gov.mohcc.impilo.devportal.domain.ApiKeyEntity;
import zw.gov.mohcc.impilo.devportal.domain.CertificationEntity;
import zw.gov.mohcc.impilo.devportal.domain.ClientEntity;
import zw.gov.mohcc.impilo.devportal.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.devportal.repository.ApiKeyRepository;
import zw.gov.mohcc.impilo.devportal.repository.CertificationRepository;
import zw.gov.mohcc.impilo.devportal.repository.ClientRepository;
import zw.gov.mohcc.impilo.devportal.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificationServiceTest {

    @Mock private ClientRepository clientRepo;
    @Mock private ApiKeyRepository keyRepo;
    @Mock private CertificationRepository certRepo;
    @Mock private OutboxEventRepository outboxRepo;

    private DeveloperPortalService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new DeveloperPortalService(clientRepo, keyRepo, certRepo, outboxRepo, objectMapper);
    }

    @Test
    void runCertification_fullyCompliant_returnsPass() {
        UUID clientId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ClientEntity client = buildClient(clientId, tenantId);
        client.setDeprecationPosture("WARN");
        client.setSandboxEnabled(true);

        ApiKeyEntity key = buildActiveKey(clientId, "Production Key");

        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(keyRepo.findByClientId(clientId)).thenReturn(List.of(key));
        when(certRepo.save(any())).thenAnswer(inv -> {
            CertificationEntity c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.runCertification(
                clientId, tenantId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "test");

        assertEquals("PASS", result.get("result"));
        assertEquals("COMPLETED", result.get("status"));
        assertEquals(7, result.get("checks_total"));
        assertEquals(7, result.get("checks_passed"));
        assertEquals(0, result.get("checks_failed"));
    }

    @Test
    void runCertification_noActiveKey_returnsFail() {
        UUID clientId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ClientEntity client = buildClient(clientId, tenantId);
        client.setDeprecationPosture("WARN");
        client.setSandboxEnabled(true);

        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(keyRepo.findByClientId(clientId)).thenReturn(List.of()); // no keys
        when(certRepo.save(any())).thenAnswer(inv -> {
            CertificationEntity c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.runCertification(
                clientId, tenantId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "test");

        assertEquals("FAIL", result.get("result"));
        assertTrue((int) result.get("checks_failed") > 0);
    }

    @Test
    void runCertification_deprecationPostureNone_failsPostureCheck() {
        UUID clientId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ClientEntity client = buildClient(clientId, tenantId);
        client.setDeprecationPosture("NONE");
        client.setSandboxEnabled(true);

        ApiKeyEntity key = buildActiveKey(clientId, "Key");

        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(keyRepo.findByClientId(clientId)).thenReturn(List.of(key));
        when(certRepo.save(any())).thenAnswer(inv -> {
            CertificationEntity c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.runCertification(
                clientId, tenantId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "test");

        assertEquals("FAIL", result.get("result"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) result.get("checks");
        var postureCheck = checks.stream()
                .filter(c -> "deprecation_posture_configured".equals(c.get("check")))
                .findFirst().orElseThrow();
        assertFalse((boolean) postureCheck.get("passed"));
    }

    @Test
    void runCertification_emitsOutboxEvent() {
        UUID clientId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ClientEntity client = buildClient(clientId, tenantId);
        client.setDeprecationPosture("WARN");
        client.setSandboxEnabled(true);

        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(keyRepo.findByClientId(clientId)).thenReturn(List.of(buildActiveKey(clientId, "Key")));
        when(certRepo.save(any())).thenAnswer(inv -> {
            CertificationEntity c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.runCertification(clientId, tenantId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "test");

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepo).save(captor.capture());
        OutboxEventEntity event = captor.getValue();
        assertEquals("impilo.developer-portal.certification.completed.v1", event.getEventType());
        assertEquals("Certification", event.getAggregateType());
    }

    @Test
    void checkFederationReadiness_fullyReady_returnsOverallTrue() {
        UUID clientId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ClientEntity client = buildClient(clientId, tenantId);
        client.setDeprecationPosture("WARN");
        client.setSandboxEnabled(true);

        CertificationEntity cert = new CertificationEntity();
        cert.setId(UUID.randomUUID());
        cert.setClientId(clientId);
        cert.setResult("PASS");
        cert.setCompletedAt(OffsetDateTime.now());

        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(keyRepo.findByClientId(clientId)).thenReturn(List.of(buildActiveKey(clientId, "K")));
        when(certRepo.findByClientIdOrderByCreatedAtDesc(clientId)).thenReturn(List.of(cert));

        Map<String, Object> result = service.checkFederationReadiness(clientId);

        assertTrue((boolean) result.get("overall_ready"));
        assertNotNull(result.get("checklist"));
    }

    @Test
    void checkFederationReadiness_noCertification_notReady() {
        UUID clientId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ClientEntity client = buildClient(clientId, tenantId);
        client.setDeprecationPosture("WARN");
        client.setSandboxEnabled(true);

        when(clientRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(keyRepo.findByClientId(clientId)).thenReturn(List.of(buildActiveKey(clientId, "K")));
        when(certRepo.findByClientIdOrderByCreatedAtDesc(clientId)).thenReturn(List.of()); // no certs

        Map<String, Object> result = service.checkFederationReadiness(clientId);

        assertFalse((boolean) result.get("overall_ready"));
    }

    @Test
    void getDashboardStats_aggregatesCorrectly() {
        UUID tenantId = UUID.randomUUID();
        UUID c1 = UUID.randomUUID(), c2 = UUID.randomUUID();

        ClientEntity client1 = buildClient(c1, tenantId);
        client1.setSandboxEnabled(true);
        ClientEntity client2 = buildClient(c2, tenantId);
        client2.setStatus("SUSPENDED");

        when(clientRepo.findByTenantId(tenantId)).thenReturn(List.of(client1, client2));

        ApiKeyEntity k1 = buildActiveKey(c1, "K1");
        ApiKeyEntity k2 = buildActiveKey(c1, "K2");
        k2.setStatus("REVOKED");
        when(keyRepo.findByClientId(c1)).thenReturn(List.of(k1, k2));
        when(keyRepo.findByClientId(c2)).thenReturn(List.of());

        CertificationEntity cert = new CertificationEntity();
        cert.setResult("PASS");
        when(certRepo.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(cert));

        Map<String, Object> stats = service.getDashboardStats(tenantId);

        assertEquals(2, stats.get("total_clients"));
        assertEquals(1L, stats.get("active_clients"));
        assertEquals(1L, stats.get("sandbox_clients"));
        assertEquals(2, stats.get("total_keys"));
        assertEquals(1L, stats.get("active_keys"));
        assertEquals(1L, stats.get("revoked_keys"));
        assertEquals(1L, stats.get("certifications_passed"));
    }

    private ClientEntity buildClient(UUID id, UUID tenantId) {
        ClientEntity c = new ClientEntity();
        c.setId(id);
        c.setTenantId(tenantId);
        c.setClientName("Test Client");
        c.setDescription("Test");
        c.setContactEmail("test@example.org");
        c.setStatus("ACTIVE");
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        return c;
    }

    private ApiKeyEntity buildActiveKey(UUID clientId, String label) {
        ApiKeyEntity k = new ApiKeyEntity();
        k.setId(UUID.randomUUID());
        k.setClientId(clientId);
        k.setKeyPrefix("imp_test");
        k.setKeyHash("hash");
        k.setLabel(label);
        k.setStatus("ACTIVE");
        k.setCreatedAt(OffsetDateTime.now());
        return k;
    }
}
