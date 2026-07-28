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
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentTemplateRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.RemoteConsentSessionRepository;
import zw.gov.mohcc.impilo.mvumo.redis.RemoteConsentTokenRegistry;
import zw.gov.mohcc.impilo.mvumo.surface.ConsentSummaryDeriver;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pipeline §28 demonstration 3 — child assent recorded distinctly from guardian consent.
 *
 * <p>V300 (Wave P5, mvumo-service) added {@code decision_maker_type}/{@code assent_sought}/
 * {@code assent_outcome}/{@code assent_notes} to {@code consent_request} and a runtime-proof rig
 * ({@code procedures-consent-depth-journeys.sh}, case J-P5-9) already proves the SCHEMA lets a
 * refused child assent coexist with a granted guardian consent. What P5 never added is a Java
 * reader/writer for any of it — {@link MvumoService#transition} whitelists only
 * actualMethod/actualAssurance/proofRef/grantedByRef/refusedReason, and {@code toRequestView}
 * exposed none of these columns. These tests prove the {@link MvumoService#recordAssent} method
 * this wave adds to close that gap, deliberately kept OUT of the grant/refuse state machine so a
 * guardian's GRANTED consent and a child's REFUSED assent cannot overwrite one another.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MvumoServiceAssentTest {

    @Mock private ConsentTemplateRepository templateRepository;
    @Mock private ConsentRequestRepository requestRepository;
    @Mock private RemoteConsentSessionRepository sessionRepository;
    @Mock private ConsentRequirementEngine requirementEngine;
    @Mock private EventOutboxRepository eventOutboxRepository;
    @Mock private ConsentEventRepository consentEventRepository;
    @Mock private TshepoConsentClient tshepoConsentClient;
    @Mock private ObjectProvider<RemoteConsentTokenRegistry> tokenRegistryProvider;
    @Mock private ConsentSummaryDeriver consentSummaryDeriver;
    @Mock private CommunicationPreferenceService communicationPreferenceService;

    private MvumoService service;
    private final UUID tenant = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final UUID requestId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(tokenRegistryProvider.getIfAvailable()).thenReturn(null);
        when(consentSummaryDeriver.derive(any(List.class))).thenReturn(Map.of());
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new MvumoService(
                templateRepository, requestRepository, sessionRepository, new ObjectMapper(),
                requirementEngine, eventOutboxRepository, consentEventRepository, tshepoConsentClient,
                tokenRegistryProvider, consentSummaryDeriver, communicationPreferenceService);
    }

    private ConsentRequestEntity request() {
        ConsentRequestEntity e = new ConsentRequestEntity();
        e.setId(requestId);
        e.setTenantId(tenant);
        e.setState("GRANTED");
        e.setSubjectPatientRef("CPID-1");
        e.setConsentType("PROCEDURE");
        e.setRequiredAssurance("L2");
        e.setCreatedAt(java.time.Instant.now());
        return e;
    }

    @Test
    void recordAssentSetsDecisionMakerAndAssentFieldsWithoutTouchingState() {
        when(requestRepository.findByIdAndTenantId(requestId, tenant)).thenReturn(Optional.of(request()));

        Map<String, Object> result = service.recordAssent(tenant, requestId, Map.of(
                "decisionMakerType", "GUARDIAN",
                "decisionMakerRef", "rel-mother-1",
                "decisionMakerBasis", "Mother, parental responsibility",
                "assentSought", "true",
                "assentOutcome", "REFUSED",
                "assentNotes", "Child declined, cried when asked"));

        assertEquals("GRANTED", result.get("state"), "assent must not overwrite the guardian's grant");
        assertEquals("GUARDIAN", result.get("decisionMakerType"));
        assertEquals("REFUSED", result.get("assentOutcome"));
        assertEquals(true, result.get("assentSought"));
        assertEquals("Child declined, cried when asked", result.get("assentNotes"));
    }

    /** §28 demonstration 3's own point: a granted guardian consent and a refused child assent coexist. */
    @Test
    void aGrantedConsentAndARefusedAssentCoexistOnTheSameRequest() {
        ConsentRequestEntity granted = request();
        granted.setState("GRANTED");
        granted.setDecisionMakerType("GUARDIAN");
        granted.setAssentOutcome("GIVEN");
        when(requestRepository.findByIdAndTenantId(requestId, tenant)).thenReturn(Optional.of(granted));

        Map<String, Object> result = service.recordAssent(tenant, requestId, Map.of("assentOutcome", "REFUSED"));

        assertEquals("GRANTED", result.get("state"));
        assertEquals("REFUSED", result.get("assentOutcome"));
    }

    @Test
    void anInventedAssentOutcomeIsRejected() {
        when(requestRepository.findByIdAndTenantId(requestId, tenant)).thenReturn(Optional.of(request()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.recordAssent(tenant, requestId, Map.of("assentOutcome", "PROBABLY_FINE")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void recordingAssentForAnUnknownRequestIsNotFound() {
        when(requestRepository.findByIdAndTenantId(requestId, tenant)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> service.recordAssent(tenant, requestId, Map.of("assentOutcome", "GIVEN")));
    }

    @Test
    void anEmptyBodyLeavesExistingFieldsUntouched() {
        ConsentRequestEntity existing = request();
        existing.setAssentOutcome("GIVEN");
        when(requestRepository.findByIdAndTenantId(requestId, tenant)).thenReturn(Optional.of(existing));

        Map<String, Object> result = service.recordAssent(tenant, requestId, Map.of());

        assertEquals("GIVEN", result.get("assentOutcome"));
    }
}
