package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.integration.*;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.*;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Wave 5b §15 — EMERGENCY SURGERY orchestration at the theatre-board service layer: rapid activation
 * (forced EMERGENCY triage, armed override, minimal safe preop, team mobilisation) and the emergency
 * consent exception (deferred / proxy / two-doctor) with a full audit trail. Never blocks life-saving
 * surgery on absent consent; every override is auditable.
 */
@ExtendWith(MockitoExtension.class)
class TheatreEmergencySurgeryTest {

    @Mock ProcedureEpisodeRepository episodeRepository;
    @Mock ProcedureReadinessCheckRepository readinessRepository;
    @Mock ProcedureNoteRepository noteRepository;
    @Mock ProcedureSafetyEventRepository safetyRepository;
    @Mock ProcedureChecklistItemRepository checklistRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock ProcedureEpisodeService episodeService;
    @Mock OrosOrderClient orosOrderClient;
    @Mock TheatreReadinessClient readinessClient;
    @Mock TheatreDeathClient deathClient;
    @Mock ButanoProcedureClient butanoClient;
    @Mock ProcedureBloodLinkRepository bloodLinkRepository;
    @Mock ProcedureTransportRepository transportRepository;
    @Mock ProcedureSpecimenRepository specimenRepository;
    @Mock ProcedureCountRepository countRepository;
    @Mock MadiBloodClient madiBloodClient;
    @Mock NhumeTransportClient nhumeTransportClient;
    @Mock OrosSpecimenClient orosSpecimenClient;
    @Mock RitoSafetyClient ritoSafetyClient;
    @Mock ProcedurePostopRecordRepository postopRepository;
    @Mock AdmissionService admissionService;
    @Mock ProcedureTeleconsultLinkRepository teleconsultLinkRepository;
    @Mock PctTeleconsultClient pctTeleconsultClient;
    @Mock FundoSurgicalLogbookClient fundoSurgicalLogbookClient;
    @Mock DaidzaiEpisodeClient daidzaiEpisodeClient;

    private TheatreService service;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TheatreService(episodeRepository, readinessRepository, noteRepository, safetyRepository,
                checklistRepository, outboxRepository, episodeService, orosOrderClient, readinessClient,
                deathClient, butanoClient, bloodLinkRepository, transportRepository, specimenRepository,
                countRepository, madiBloodClient, nhumeTransportClient, orosSpecimenClient, ritoSafetyClient,
                postopRepository, admissionService, teleconsultLinkRepository, pctTeleconsultClient,
                fundoSurgicalLogbookClient, daidzaiEpisodeClient, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(tenant, "actor-emergency-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    private ProcedureEpisodeEntity episode(UUID id, String status, String consentStatus) {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(id);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-EMERG");
        e.setStatus(status);
        e.setConsentStatus(consentStatus);
        e.setSurgeonId("PROV-ZW-00020");
        e.setAnaesthetistId("PROV-ZW-00040");
        return e;
    }

    // ── Rapid activation ─────────────────────────────────────────────────────────────────────────

    @Test
    void activateForcesEmergencyTriageArmsOverrideAndRecordsMinimalPreop() {
        UUID id = UUID.randomUUID();
        ProcedureEpisodeEntity e = episode(id, "BOOKED", "PENDING");
        when(episodeRepository.findByTenantIdAndOrosOrderId(eq(tenant), any())).thenReturn(Optional.empty());
        when(episodeService.createEpisode(anyMap())).thenReturn(new LinkedHashMap<>(Map.of("id", id.toString())));
        when(episodeRepository.findById(id)).thenReturn(Optional.of(e));
        when(episodeService.getEpisode(id)).thenReturn(new LinkedHashMap<>(Map.of("id", id.toString())));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patientId", "CPID-EMERG");
        body.put("procedureName", "Emergency laparotomy");
        body.put("orosOrderId", "RIG-ORDER-EMERG");   // supply order id → no OROS placeOrder needed
        body.put("indication", "Haemorrhagic shock — free fluid on FAST");
        // No triagePriority supplied → rapid activation forces EMERGENCY.

        Map<String, Object> out = service.activateEmergencySurgery(body);

        assertEquals("EMERGENCY", e.getTriagePriority(), "rapid activation is never elective");
        assertTrue(e.isEmergencyOverride(), "the emergency override must be armed");
        assertEquals(Boolean.TRUE, out.get("emergency_override"));
        // Minimal safe preop recorded (reduced set, not the full elective assessment).
        verify(episodeService).submitPreop(eq(id), argThat(m -> "NURSING".equals(m.get("assessmentType"))));
        // Team mobilisation + activation are both emitted for the on-call/notification consumers.
        verify(outboxRepository, atLeast(2)).save(any());
    }

    // ── Emergency consent exception (DEFERRED / PROXY / TWO_DOCTOR) ─────────────────────────────────

    @Test
    void deferredConsentExceptionSetsExceptionStatusArmsOverrideAndAudits() {
        UUID id = UUID.randomUUID();
        ProcedureEpisodeEntity e = episode(id, "READY_FOR_THEATRE", "PENDING");
        when(episodeRepository.findById(id)).thenReturn(Optional.of(e));
        when(episodeService.getEpisode(id)).thenReturn(new LinkedHashMap<>());

        Map<String, Object> out = service.recordEmergencyConsentException(id, Map.of(
                "basis", "deferred", "reason", "Unconscious polytrauma, no proxy available — life-saving"));

        assertEquals("EMERGENCY_EXCEPTION", e.getConsentStatus());
        assertTrue(e.isEmergencyOverride());
        assertNotNull(e.getConsentProofRef(), "the exception basis + justification must be recorded for audit");
        assertEquals("DEFERRED", out.get("emergency_consent_basis"));

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals("theatre.consent.emergency-exception", outbox.getValue().getEventType());
    }

    @Test
    void consentExceptionRequiresAJustification() {
        UUID id = UUID.randomUUID();
        when(episodeRepository.findById(id)).thenReturn(Optional.of(episode(id, "READY_FOR_THEATRE", "PENDING")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.recordEmergencyConsentException(id, Map.of("basis", "DEFERRED")));
        assertEquals(400, ex.getStatusCode().value());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void twoDoctorExceptionRequiresASecondClinician() {
        UUID id = UUID.randomUUID();
        when(episodeRepository.findById(id)).thenReturn(Optional.of(episode(id, "READY_FOR_THEATRE", "PENDING")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.recordEmergencyConsentException(id, Map.of(
                        "basis", "TWO_DOCTOR", "reason", "Cannot obtain consent")));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void proxyExceptionRequiresTheProxy() {
        UUID id = UUID.randomUUID();
        when(episodeRepository.findById(id)).thenReturn(Optional.of(episode(id, "READY_FOR_THEATRE", "PENDING")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.recordEmergencyConsentException(id, Map.of(
                        "basis", "PROXY", "reason", "Patient obtunded")));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void basisNormalisationDefaultsToDeferredAndAcceptsHyphenForms() {
        assertEquals("TWO_DOCTOR", TheatreService.normaliseConsentExceptionBasis("two-doctor"));
        assertEquals("PROXY", TheatreService.normaliseConsentExceptionBasis("Proxy"));
        assertEquals("DEFERRED", TheatreService.normaliseConsentExceptionBasis(null));
        assertEquals("DEFERRED", TheatreService.normaliseConsentExceptionBasis("made-up"));
    }
}
