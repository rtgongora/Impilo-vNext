package zw.gov.mohcc.impilo.mvumo.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;
import zw.gov.mohcc.impilo.mvumo.service.MvumoService;
import zw.gov.mohcc.impilo.mvumo.service.TenantIds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@SpringBootTest
@ActiveProfiles("test")
class MvumoCrossServiceFlowIT {

    @Autowired
    private MvumoService mvumoService;

    @Autowired
    private ConsentRequestRepository consentRequestRepository;

    @Autowired
    private ConsentEventRepository consentEventRepository;

    @Autowired
    private EventOutboxRepository eventOutboxRepository;

    @MockBean
    private TshepoConsentClient tshepoConsentClient;

    @Test
    void remoteVerifyAndGrantPersistsStateAndWritesAuditOutbox() {
        UUID tshepoDirectiveId = UUID.randomUUID();
        when(tshepoConsentClient.createDirective(anyMap())).thenReturn(tshepoDirectiveId);

        Map<String, Object> req = mvumoService.createConsentRequest(TenantIds.PLATFORM, Map.of(
                "subjectPatientRef", "Patient/e2e-1",
                "consentType", "DIGITAL_TELEMEDICINE",
                "requiredAssurance", "L1_SIMPLE_DIGITAL"));
        UUID consentRequestId = UUID.fromString(req.get("id").toString());

        Map<String, Object> session = mvumoService.createRemoteSession(TenantIds.PLATFORM, Map.of(
                "consentRequestId", consentRequestId.toString(),
                "channel", "TOKEN_LINK",
                "ttlMinutes", 30));

        UUID sessionId = UUID.fromString(session.get("sessionId").toString());
        String token = session.get("token").toString();

        Map<String, Object> verified = mvumoService.remoteVerify(
                TenantIds.PLATFORM,
                sessionId,
                "provider-123",
                "TREATMENT",
                UUID.randomUUID().toString(),
                Map.of("token", token));
        assertThat(verified.get("sessionState")).isEqualTo("VERIFIED");

        Map<String, Object> granted = mvumoService.remoteGrant(
                TenantIds.PLATFORM,
                sessionId,
                "provider-123",
                "TREATMENT",
                UUID.randomUUID().toString(),
                Map.of("grantedByRef", "provider-123", "reason", "patient-accepted"));
        assertThat(granted.get("sessionState")).isEqualTo("GRANTED");
        assertThat(granted.get("consentState")).isEqualTo("GRANTED");

        var persisted = consentRequestRepository.findByIdAndTenantId(consentRequestId, TenantIds.PLATFORM).orElseThrow();
        assertThat(persisted.getState()).isEqualTo("GRANTED");
        assertThat(persisted.getTshepoConsentId()).isEqualTo(tshepoDirectiveId);

        assertThat(consentEventRepository.findByTenantIdAndConsentIdOrderByCreatedAtDesc(TenantIds.PLATFORM, consentRequestId))
                .extracting("eventType")
                .contains("CONSENT_GRANTED", "REMOTE_SESSION_GRANTED", "REMOTE_SESSION_VERIFIED");
        assertThat(eventOutboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .isNotEmpty();
    }

    @Test
    void remoteRefusePersistsRefusedStateAndAuditEvidence() {
        Map<String, Object> req = mvumoService.createConsentRequest(TenantIds.PLATFORM, Map.of(
                "subjectPatientRef", "Patient/e2e-2",
                "consentType", "DIGITAL_TELEMEDICINE",
                "requiredAssurance", "L1_SIMPLE_DIGITAL"));
        UUID consentRequestId = UUID.fromString(req.get("id").toString());

        Map<String, Object> session = mvumoService.createRemoteSession(TenantIds.PLATFORM, Map.of(
                "consentRequestId", consentRequestId.toString(),
                "channel", "TOKEN_LINK",
                "ttlMinutes", 30));
        UUID sessionId = UUID.fromString(session.get("sessionId").toString());
        String token = session.get("token").toString();

        mvumoService.remoteVerify(
                TenantIds.PLATFORM,
                sessionId,
                "provider-456",
                "TREATMENT",
                UUID.randomUUID().toString(),
                Map.of("token", token));

        Map<String, Object> refused = mvumoService.remoteRefuse(
                TenantIds.PLATFORM,
                sessionId,
                "provider-456",
                "TREATMENT",
                UUID.randomUUID().toString(),
                Map.of("refusedReason", "patient-declined"));
        assertThat(refused.get("sessionState")).isEqualTo("REFUSED");
        assertThat(refused.get("consentState")).isEqualTo("REFUSED");

        var persisted = consentRequestRepository.findByIdAndTenantId(consentRequestId, TenantIds.PLATFORM).orElseThrow();
        assertThat(persisted.getState()).isEqualTo("REFUSED");
        assertThat(persisted.getRefusedReason()).isEqualTo("patient-declined");
    }

    @Test
    void dependencyFailureNeverReturnsSuccessForRemoteGrant() {
        when(tshepoConsentClient.createDirective(anyMap())).thenThrow(new IllegalStateException("tshepo down"));

        Map<String, Object> req = mvumoService.createConsentRequest(TenantIds.PLATFORM, Map.of(
                "subjectPatientRef", "Patient/e2e-3",
                "consentType", "DIGITAL_TELEMEDICINE",
                "requiredAssurance", "L1_SIMPLE_DIGITAL"));
        UUID consentRequestId = UUID.fromString(req.get("id").toString());

        Map<String, Object> session = mvumoService.createRemoteSession(TenantIds.PLATFORM, Map.of(
                "consentRequestId", consentRequestId.toString(),
                "channel", "TOKEN_LINK",
                "ttlMinutes", 30));
        UUID sessionId = UUID.fromString(session.get("sessionId").toString());
        String token = session.get("token").toString();

        mvumoService.remoteVerify(
                TenantIds.PLATFORM,
                sessionId,
                "provider-789",
                "TREATMENT",
                UUID.randomUUID().toString(),
                Map.of("token", token));

        assertThatThrownBy(() -> mvumoService.remoteGrant(
                TenantIds.PLATFORM,
                sessionId,
                "provider-789",
                "TREATMENT",
                UUID.randomUUID().toString(),
                new HashMap<>()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE));
    }
}
