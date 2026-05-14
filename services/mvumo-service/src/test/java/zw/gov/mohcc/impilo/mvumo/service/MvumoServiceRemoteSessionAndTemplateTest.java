package zw.gov.mohcc.impilo.mvumo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mvumo.engine.ConsentRequirementEngine;
import zw.gov.mohcc.impilo.mvumo.integration.TshepoConsentClient;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentTemplateEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentTemplateRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.RemoteConsentSessionEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.RemoteConsentSessionRepository;
import zw.gov.mohcc.impilo.mvumo.redis.RemoteConsentTokenRegistry;
import zw.gov.mohcc.impilo.mvumo.surface.ConsentSummaryDeriver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MvumoServiceRemoteSessionAndTemplateTest {

    @Mock
    private ConsentTemplateRepository templateRepository;
    @Mock
    private ConsentRequestRepository requestRepository;
    @Mock
    private RemoteConsentSessionRepository sessionRepository;
    @Mock
    private ConsentRequirementEngine requirementEngine;
    @Mock
    private EventOutboxRepository eventOutboxRepository;
    @Mock
    private ConsentEventRepository consentEventRepository;
    @Mock
    private TshepoConsentClient tshepoConsentClient;
    @Mock
    private ObjectProvider<RemoteConsentTokenRegistry> tokenRegistryProvider;
    @Mock
    private ConsentSummaryDeriver consentSummaryDeriver;
    @Mock
    private CommunicationPreferenceService communicationPreferenceService;

    private MvumoService service;

    @BeforeEach
    void setUp() {
        when(tokenRegistryProvider.getIfAvailable()).thenReturn(null);
        when(consentSummaryDeriver.derive(any(List.class))).thenReturn(Map.of());
        when(requestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(templateRepository.save(any())).thenAnswer(invocation -> {
            ConsentTemplateEntity e = invocation.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
        when(consentEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new MvumoService(
                templateRepository,
                requestRepository,
                sessionRepository,
                new ObjectMapper(),
                requirementEngine,
                eventOutboxRepository,
                consentEventRepository,
                tshepoConsentClient,
                tokenRegistryProvider,
                consentSummaryDeriver,
                communicationPreferenceService);
    }

    @Test
    void remoteVerifyRejectsMissingToken() {
        UUID tenantId = TenantIds.PLATFORM;
        RemoteConsentSessionEntity session = session("OPEN");
        ConsentRequestEntity request = request(session.getConsentRequestId(), tenantId);
        when(sessionRepository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));
        when(requestRepository.findByIdAndTenantId(session.getConsentRequestId(), tenantId)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.remoteVerify(tenantId, session.getId(), "actor-1", "TREATMENT", "corr-1", Map.of()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void remoteVerifyTransitionsSessionToVerified() {
        UUID tenantId = TenantIds.PLATFORM;
        String token = "raw-token";
        RemoteConsentSessionEntity session = session("OPEN");
        session.setTokenHash(sha256Hex(token));
        ConsentRequestEntity request = request(session.getConsentRequestId(), tenantId);
        when(sessionRepository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));
        when(requestRepository.findByIdAndTenantId(session.getConsentRequestId(), tenantId)).thenReturn(Optional.of(request));

        Map<String, Object> result = service.remoteVerify(
                tenantId, session.getId(), "actor-1", "TREATMENT", "corr-1", Map.of("token", token));

        assertEquals("VERIFIED", result.get("sessionState"));
        verify(consentEventRepository).save(any(ConsentEventEntity.class));
    }

    @Test
    void remoteGrantRequiresVerifiedSession() {
        UUID tenantId = TenantIds.PLATFORM;
        RemoteConsentSessionEntity session = session("OPEN");
        when(sessionRepository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.remoteGrant(tenantId, session.getId(), "actor-1", "TREATMENT", "corr-1", Map.of()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void remoteGrantTransitionsConsentAndSession() {
        UUID tenantId = TenantIds.PLATFORM;
        UUID tshepoDirectiveId = UUID.randomUUID();
        RemoteConsentSessionEntity session = session("VERIFIED");
        ConsentRequestEntity request = request(session.getConsentRequestId(), tenantId);
        request.setState("PENDING_SIGNATURE");
        when(sessionRepository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));
        when(requestRepository.findByIdAndTenantId(session.getConsentRequestId(), tenantId)).thenReturn(Optional.of(request));
        when(tshepoConsentClient.createDirective(any())).thenReturn(tshepoDirectiveId);

        Map<String, Object> result = service.remoteGrant(
                tenantId, session.getId(), "actor-1", "TREATMENT", "corr-1", Map.of("grantedByRef", "actor-1"));

        assertEquals("GRANTED", result.get("sessionState"));
        assertEquals("GRANTED", result.get("consentState"));
        verify(tshepoConsentClient).createDirective(any());
        verify(eventOutboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void createTemplateAssignsVersionAndPersists() {
        UUID tenantId = TenantIds.PLATFORM;
        when(templateRepository.findTopByTenantIdAndTemplateKeyOrderByVersionDesc(tenantId, "telemedicine"))
                .thenReturn(Optional.empty());

        Map<String, Object> result = service.createTemplate(tenantId, Map.of(
                "templateKey", "telemedicine",
                "consentType", "DIGITAL",
                "title", "Telemedicine",
                "bodyMarkdown", "Consent body"));

        assertEquals(1, result.get("version"));
        assertEquals("telemedicine", result.get("templateKey"));
        assertNotNull(result.get("id"));
    }

    @Test
    void updateTemplateCreatesNewVersionAndRetiresPrevious() {
        UUID tenantId = TenantIds.PLATFORM;
        UUID templateId = UUID.randomUUID();
        ConsentTemplateEntity current = new ConsentTemplateEntity();
        current.setId(templateId);
        current.setTenantId(tenantId);
        current.setTemplateKey("telemedicine");
        current.setVersion(2);
        current.setLanguage("en");
        current.setConsentType("DIGITAL");
        current.setRequiredAssurance("L1_SIMPLE_DIGITAL");
        current.setTitle("Old title");
        current.setBodyMarkdown("Old body");
        current.setAllowedMethodsJson("[]");
        current.setRemoteAllowed(true);
        current.setOfflineAllowed(true);
        when(templateRepository.findByIdAndTenantId(templateId, tenantId)).thenReturn(Optional.of(current));

        Map<String, Object> result = service.updateTemplate(tenantId, templateId, Map.of("title", "Updated title"));

        assertEquals(3, result.get("version"));
        assertEquals("Updated title", result.get("title"));
        assertNotNull(current.getRetiredAt());
    }

    private static RemoteConsentSessionEntity session(String state) {
        RemoteConsentSessionEntity s = new RemoteConsentSessionEntity();
        s.setId(UUID.randomUUID());
        s.setTenantId(TenantIds.PLATFORM);
        s.setConsentRequestId(UUID.randomUUID());
        s.setState(state);
        s.setChannel("TOKEN_LINK");
        s.setTokenHash(sha256Hex("placeholder"));
        s.setExpiresAt(Instant.now().plus(20, ChronoUnit.MINUTES));
        return s;
    }

    private static ConsentRequestEntity request(UUID id, UUID tenantId) {
        ConsentRequestEntity r = new ConsentRequestEntity();
        r.setId(id);
        r.setTenantId(tenantId);
        r.setState("PENDING_SIGNATURE");
        r.setSubjectPatientRef("Patient/test");
        r.setConsentType("DIGITAL");
        r.setRequiredAssurance("L1_SIMPLE_DIGITAL");
        r.setContextJson("{}");
        r.setCreatedAt(Instant.now());
        return r;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
