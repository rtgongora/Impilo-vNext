package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.integration.*;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.*;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Wave 4 §12/§13 — PACU discharge-readiness gate + escalation routing + theatre→inpatient continuity.
 * ProcedureEpisodeService and peer clients are mocked; these assert the theatre orchestration behaviour.
 */
@ExtendWith(MockitoExtension.class)
class TheatrePacuRecoveryTest {

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

    private TheatreService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TheatreService(episodeRepository, readinessRepository, noteRepository, safetyRepository,
                checklistRepository, outboxRepository, episodeService, orosOrderClient, readinessClient,
                deathClient, butanoClient, bloodLinkRepository, transportRepository, specimenRepository,
                countRepository, madiBloodClient, nhumeTransportClient, orosSpecimenClient, ritoSafetyClient,
                postopRepository, admissionService, teleconsultLinkRepository, pctTeleconsultClient,
                fundoSurgicalLogbookClient, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(tenant, "actor-recovery-nurse", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), facilityId, null, null, AccessMode.INTERNAL));
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-9");
        e.setProcedureName("Appendicectomy");
        e.setStatus("PACU");
        lenient().when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        lenient().when(episodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(postopRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(safetyRepository.save(any())).thenAnswer(i -> {
            ProcedureSafetyEventEntity ev = i.getArgument(0);
            if (ev.getSafetyEventId() == null) ev.setSafetyEventId(UUID.randomUUID());
            return ev;
        });
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    @Test
    void wardDischargeBlockedWhenNotDischargeReady() {
        when(episodeService.computeDischargeReadiness(episodeId))
                .thenReturn(Map.of("ready", false, "band", "HIGH"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.pacuDischargeDecision(episodeId, Map.of("destination", "WARD")));
        assertEquals(409, ex.getStatusCode().value());
        // No admission or transport should be attempted on a blocked discharge.
        verifyNoInteractions(admissionService);
        verifyNoInteractions(nhumeTransportClient);
    }

    @Test
    void wardDischargeReadyCreatesAdmissionLinkAndNhumeMovement() {
        when(episodeService.computeDischargeReadiness(episodeId))
                .thenReturn(Map.of("ready", true, "band", "LOW"));
        when(episodeService.completePostop(eq(episodeId), anyMap())).thenReturn(new java.util.LinkedHashMap<>());
        when(postopRepository.findByEpisodeId(episodeId)).thenReturn(Optional.of(new ProcedurePostopRecordEntity()));
        when(admissionService.findActiveAdmissionsForPatientAtFacility("CPID-9", facilityId)).thenReturn(List.of());
        UUID admissionRef = UUID.randomUUID();
        AdmissionEntity admission = new AdmissionEntity();
        admission.setAdmissionRef(admissionRef);
        when(admissionService.createAdmission(any())).thenReturn(admission);
        when(nhumeTransportClient.createDelivery(eq("PATIENT"), eq("PACU_TO_WARD"), any(), any(), any(), any()))
                .thenReturn(new NhumeTransportClient.DeliveryView("NHUME-1", "DISPATCHED", null, true));
        when(transportRepository.countByEpisodeIdAndLeg(episodeId, "PACU_TO_WARD")).thenReturn(0);
        when(transportRepository.save(any())).thenAnswer(i -> {
            ProcedureTransportEntity t = i.getArgument(0);
            if (t.getTransportId() == null) t.setTransportId(UUID.randomUUID());
            return t;
        });

        Map<String, Object> out = service.pacuDischargeDecision(episodeId, Map.of("destination", "WARD"));

        assertEquals(admissionRef.toString(), out.get("admission_ref"));
        assertEquals("NHUME-1", out.get("nhume_delivery_id"));
        verify(admissionService).createAdmission(any());
        verify(nhumeTransportClient).createDelivery(eq("PATIENT"), eq("PACU_TO_WARD"), any(), any(), any(), any());
    }

    @Test
    void unplannedIcuTransferIsFlaggedAndRoutesIcuLeg() {
        when(episodeService.computeDischargeReadiness(episodeId))
                .thenReturn(Map.of("ready", false, "band", "HIGH"));
        when(episodeService.completePostop(eq(episodeId), anyMap())).thenReturn(new java.util.LinkedHashMap<>());
        when(postopRepository.findByEpisodeId(episodeId)).thenReturn(Optional.of(new ProcedurePostopRecordEntity()));
        when(admissionService.findActiveAdmissionsForPatientAtFacility("CPID-9", facilityId)).thenReturn(List.of());
        AdmissionEntity admission = new AdmissionEntity();
        admission.setAdmissionRef(UUID.randomUUID());
        when(admissionService.createAdmission(any())).thenReturn(admission);
        when(nhumeTransportClient.createDelivery(eq("PATIENT"), eq("PACU_TO_ICU"), any(), any(), any(), any()))
                .thenReturn(new NhumeTransportClient.DeliveryView("NHUME-ICU", "DISPATCHED", null, true));
        when(transportRepository.countByEpisodeIdAndLeg(episodeId, "PACU_TO_ICU")).thenReturn(0);
        when(transportRepository.save(any())).thenAnswer(i -> {
            ProcedureTransportEntity t = i.getArgument(0);
            if (t.getTransportId() == null) t.setTransportId(UUID.randomUUID());
            return t;
        });

        // ICU is NOT readiness-gated (a deteriorating patient going to ICU must not be blocked).
        Map<String, Object> out = service.pacuDischargeDecision(episodeId,
                Map.of("destination", "ICU", "unplanned", true));

        assertEquals(Boolean.TRUE, out.get("unplanned_icu"));
        verify(nhumeTransportClient).createDelivery(eq("PATIENT"), eq("PACU_TO_ICU"), any(), any(), any(), any());
    }

    @Test
    void completeCasePostsTraineeCpdAndIsFailSafe() {
        when(episodeService.completeEpisode(eq(episodeId), anyMap()))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of("status", "COMPLETED")));
        // Simulate a learning-service outage — the client returns false; completion must NOT be blocked.
        when(fundoSurgicalLogbookClient.postSurgicalLogbook(any(), eq("PROV-TRAINEE"), any(), any(), anyInt(), any()))
                .thenReturn(false);

        Map<String, Object> out = service.completeCase(episodeId, Map.of("traineeProviderId", "PROV-TRAINEE"));

        assertEquals("COMPLETED", out.get("status"));
        verify(fundoSurgicalLogbookClient).postSurgicalLogbook(any(), eq("PROV-TRAINEE"), any(), any(), anyInt(), any());
    }

    @Test
    void completeCaseWithoutTraineeSkipsCpd() {
        when(episodeService.completeEpisode(eq(episodeId), anyMap()))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of("status", "COMPLETED")));

        service.completeCase(episodeId, Map.of());

        verifyNoInteractions(fundoSurgicalLogbookClient);
    }

    @Test
    void escalationRoutesToRitoAndStampsPostop() {
        when(ritoSafetyClient.createCase(any(), any(), any(), any(), any(), any(), anyMap(), any()))
                .thenReturn("RITO-CASE-1");
        when(postopRepository.findByEpisodeId(episodeId)).thenReturn(Optional.of(new ProcedurePostopRecordEntity()));

        Map<String, Object> out = service.escalatePacu(episodeId,
                Map.of("escalateTo", "ANAESTHETIST", "reason", "SpO2 falling", "severity", "CRITICAL"));

        assertEquals("ROUTED", out.get("routed_status"));
        assertEquals("RITO-CASE-1", out.get("owner_ref"));
        verify(ritoSafetyClient).createCase(any(), any(), any(), eq("CRITICAL"), any(), eq("CPID-9"), anyMap(), any());
        verify(safetyRepository).save(any());
        verify(postopRepository).save(argThat(p -> "ANAESTHETIST".equals(((ProcedurePostopRecordEntity) p).getEscalatedTo())));
    }
}
