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
 * Wave 5a §15 — non-trauma alternative theatre journeys at the service layer:
 * structured cancellation reason codes (+ reschedulability that drives the waitlist return),
 * return-to-theatre transition, and multi-case isolation (no cross-contamination).
 * ProcedureEpisodeService and peer clients are mocked.
 */
@ExtendWith(MockitoExtension.class)
class TheatreAltJourneysTest {

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
        TrustContextHolder.set(new TrustContext(tenant, "actor-theatre-coordinator", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    private ProcedureEpisodeEntity episode(UUID id, String status, String triage) {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(id);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-" + id.toString().substring(0, 4));
        e.setProcedureName("Elective procedure");
        e.setStatus(status);
        e.setTriagePriority(triage);
        return e;
    }

    // ── Static reason-code contract ──────────────────────────────────────────────────────────────

    @Test
    void reasonCodeNormalisationAcceptsAllStructuredCodesAndHyphenSpaceForms() {
        assertEquals("PATIENT_NOT_FIT", TheatreService.normaliseCancellationReasonCode("patient-not-fit"));
        assertEquals("NO_BLOOD", TheatreService.normaliseCancellationReasonCode("No Blood"));
        assertEquals("EMERGENCY_DISPLACEMENT", TheatreService.normaliseCancellationReasonCode("EMERGENCY_DISPLACEMENT"));
        assertNull(TheatreService.normaliseCancellationReasonCode("made-up-code"));
        assertNull(TheatreService.normaliseCancellationReasonCode(null));
        // Full structured set present.
        assertEquals(9, TheatreService.CANCELLATION_REASON_CODES.size());
    }

    @Test
    void reschedulabilityIsTrueExceptForPatientNonAttendance() {
        assertTrue(TheatreService.isReschedulableCancellation("NO_BED"));
        assertTrue(TheatreService.isReschedulableCancellation("EQUIPMENT_FAILURE"));
        assertFalse(TheatreService.isReschedulableCancellation("PATIENT_NON_ATTENDANCE"));
        assertFalse(TheatreService.isReschedulableCancellation(null));
    }

    // ── Cancellation journey (§15 cancelled case) ────────────────────────────────────────────────

    @Test
    void cancelWithStructuredReasonCodePersistsCodeAndEmitsReschedulable() {
        UUID id = UUID.randomUUID();
        ProcedureEpisodeEntity e = episode(id, "READY_FOR_THEATRE", "ELECTIVE");
        when(episodeRepository.findById(id)).thenReturn(Optional.of(e));
        when(episodeService.getEpisode(id)).thenReturn(new LinkedHashMap<>(Map.of("id", id.toString())));

        Map<String, Object> out = service.cancel(id,
                Map.of("reasonCode", "no-blood", "reason", "cross-match not ready"));

        assertEquals("CANCELLED", e.getStatus());
        assertEquals("NO_BLOOD", out.get("reason_code"));
        assertEquals("cross-match not ready", out.get("reason_text"));
        assertEquals(Boolean.TRUE, out.get("reschedulable"));
        assertTrue(e.getCancellationReason().startsWith("NO_BLOOD"),
                "structured code persisted in reason column: " + e.getCancellationReason());
        verify(outboxRepository).save(any());
    }

    @Test
    void cancelWithPatientDnaIsNotReschedulable() {
        UUID id = UUID.randomUUID();
        when(episodeRepository.findById(id)).thenReturn(Optional.of(episode(id, "BOOKED", "ELECTIVE")));
        when(episodeService.getEpisode(id)).thenReturn(new LinkedHashMap<>());

        Map<String, Object> out = service.cancel(id, Map.of("reasonCode", "PATIENT_NON_ATTENDANCE"));

        assertEquals("PATIENT_NON_ATTENDANCE", out.get("reason_code"));
        assertEquals(Boolean.FALSE, out.get("reschedulable"));
    }

    @Test
    void cancelRejectsUnknownReasonCode() {
        UUID id = UUID.randomUUID();
        when(episodeRepository.findById(id)).thenReturn(Optional.of(episode(id, "BOOKED", "ELECTIVE")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cancel(id, Map.of("reasonCode", "surgeon-overslept")));
        assertEquals(400, ex.getStatusCode().value());
        // The case must NOT have been cancelled.
        verify(episodeRepository, never()).save(any());
    }

    @Test
    void cancelStillAcceptsFreeTextForBackwardCompatibility() {
        UUID id = UUID.randomUUID();
        ProcedureEpisodeEntity e = episode(id, "PREOP", "ELECTIVE");
        when(episodeRepository.findById(id)).thenReturn(Optional.of(e));
        when(episodeService.getEpisode(id)).thenReturn(new LinkedHashMap<>());

        Map<String, Object> out = service.cancel(id, Map.of("reason", "ward flooding — theatre closed"));

        assertEquals("CANCELLED", e.getStatus());
        assertNull(out.get("reason_code"));
        assertEquals("ward flooding — theatre closed", e.getCancellationReason());
        // Unknown structured cause defaults to reschedulable (returns to the waitlist).
        assertEquals(Boolean.TRUE, out.get("reschedulable"));
    }

    @Test
    void cancelRequiresAReason() {
        UUID id = UUID.randomUUID();
        when(episodeRepository.findById(id)).thenReturn(Optional.of(episode(id, "BOOKED", "ELECTIVE")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cancel(id, Map.of()));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void cannotCancelACompletedCase() {
        UUID id = UUID.randomUUID();
        when(episodeRepository.findById(id)).thenReturn(Optional.of(episode(id, "COMPLETED", "ELECTIVE")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cancel(id, Map.of("reasonCode", "NO_BED")));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ── Return-to-theatre (§15 complication — re-operative flow) ─────────────────────────────────

    @Test
    void returnToTheatreDelegatesToEpisodeServiceAndEmitsEvent() {
        UUID id = UUID.randomUUID();
        when(episodeRepository.findById(id)).thenReturn(Optional.of(episode(id, "PACU", "ELECTIVE")));
        when(episodeService.returnToTheatre(eq(id), anyMap()))
                .thenReturn(new LinkedHashMap<>(Map.of("status", "READY_FOR_THEATRE")));

        Map<String, Object> out = service.returnToTheatre(id, Map.of("reason", "post-op bleeding"));

        assertEquals("READY_FOR_THEATRE", out.get("status"));
        verify(episodeService).returnToTheatre(eq(id), anyMap());
        verify(outboxRepository).save(any());
    }

    // ── Multiple concurrent cases (§15 concurrency — no cross-contamination) ─────────────────────

    @Test
    void concurrentCasesAreCancelledIndependentlyWithoutCrossContamination() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ProcedureEpisodeEntity a = episode(idA, "READY_FOR_THEATRE", "ELECTIVE");
        ProcedureEpisodeEntity b = episode(idB, "PREOP", "DAY_CASE");
        when(episodeRepository.findById(idA)).thenReturn(Optional.of(a));
        when(episodeRepository.findById(idB)).thenReturn(Optional.of(b));
        when(episodeService.getEpisode(any())).thenReturn(new LinkedHashMap<>());

        Map<String, Object> outA = service.cancel(idA, Map.of("reasonCode", "NO_BED"));
        Map<String, Object> outB = service.cancel(idB, Map.of("reasonCode", "PATIENT_NON_ATTENDANCE"));

        // Each case keeps its own reason and reschedulability — no bleed-through.
        assertEquals("NO_BED", outA.get("reason_code"));
        assertEquals(Boolean.TRUE, outA.get("reschedulable"));
        assertEquals("PATIENT_NON_ATTENDANCE", outB.get("reason_code"));
        assertEquals(Boolean.FALSE, outB.get("reschedulable"));
        assertTrue(a.getCancellationReason().startsWith("NO_BED"));
        assertTrue(b.getCancellationReason().startsWith("PATIENT_NON_ATTENDANCE"));

        // Two distinct episodes saved, each with its own state.
        ArgumentCaptor<ProcedureEpisodeEntity> saved = ArgumentCaptor.forClass(ProcedureEpisodeEntity.class);
        verify(episodeRepository, times(2)).save(saved.capture());
        assertEquals(2, saved.getAllValues().stream().map(ProcedureEpisodeEntity::getEpisodeId).distinct().count());
    }
}
