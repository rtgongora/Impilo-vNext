package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.integration.CostaServiceAccessClient;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.*;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Wave 5b §15 — the emergency consent exception at the pipeline start-gate. Life-saving surgery must NOT
 * be blocked on absent ordinary consent: when consent_status = EMERGENCY_EXCEPTION and an emergency
 * override is armed (recorded + audited upstream), {@code startProcedure} bypasses the GRANTED consent
 * gate — but does NOT mark consent verified (honest: it was excepted, not obtained). The ordinary gate
 * still fires for a non-emergency case.
 */
@ExtendWith(MockitoExtension.class)
class ProcedureEmergencyStartTest {

    @Mock ProcedureEpisodeRepository episodeRepository;
    @Mock ProcedurePreopAssessmentRepository preopRepository;
    @Mock ProcedureChecklistItemRepository checklistRepository;
    @Mock ProcedureIntraopEventRepository intraopRepository;
    @Mock ProcedurePostopRecordRepository postopRepository;
    @Mock ProcedurePacuObservationRepository pacuObservationRepository;
    @Mock ProcedureEpisodeDocumentRepository documentRepository;
    @Mock ProcedureConsumableRepository consumableRepository;
    @Mock ProcedureImplantRepository implantRepository;
    @Mock ProcedureAnaesthesiaScoreRepository anaesthesiaScoreRepository;
    @Mock ProcedureCountRepository countRepository;
    @Mock ProcedureAnaesthesiaChartEntryRepository chartRepository;
    @Mock ProcedureBloodLinkRepository bloodLinkRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock ProcedureConsentRepository procedureConsentRepository;
    @Mock CostaServiceAccessClient costaServiceAccessClient;

    private ProcedureEpisodeService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProcedureEpisodeService(episodeRepository, preopRepository, checklistRepository,
                intraopRepository, postopRepository, pacuObservationRepository, documentRepository, consumableRepository,
                implantRepository, anaesthesiaScoreRepository, countRepository, chartRepository, bloodLinkRepository,
                outboxRepository, procedureConsentRepository, new ObjectMapper(), costaServiceAccessClient);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    private ProcedureEpisodeEntity episode(String status, String consentStatus, boolean override) {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-EMERG");
        e.setStatus(status);
        e.setConsentStatus(consentStatus);
        e.setEmergencyOverride(override);
        // Wave P12 §23: this whole file is about emergency scenarios, so triage priority is
        // set to match — matters only for the one test that reaches the new financial-clearance
        // gate at all (the consent exception fully succeeds), where it triggers that gate's own
        // emergency bypass rather than needing costaServiceAccessClient stubbed.
        e.setTriagePriority("EMERGENCY");
        return e;
    }

    @Test
    void startBypassesConsentGateUnderEmergencyExceptionWithOverride() {
        ProcedureEpisodeEntity e = episode("READY_FOR_THEATRE", "EMERGENCY_EXCEPTION", true);
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));

        service.startProcedure(episodeId, Map.of());

        assertEquals("IN_PROGRESS", e.getStatus(), "emergency surgery must start under the consent exception");
        assertFalse(e.isConsentVerified(), "consent must NOT be marked verified — it was excepted, not obtained");
        // The consent-bundle GRANTED loop is skipped entirely for the exception path.
        verify(procedureConsentRepository, never()).findByEpisodeId(episodeId);
    }

    @Test
    void emergencyExceptionStillRequiresAnArmedOverride() {
        ProcedureEpisodeEntity e = episode("READY_FOR_THEATRE", "EMERGENCY_EXCEPTION", false);
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.startProcedure(episodeId, Map.of()));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals("READY_FOR_THEATRE", e.getStatus());
    }

    @Test
    void ordinaryConsentGateStillBlocksANonEmergencyCase() {
        ProcedureEpisodeEntity e = episode("READY_FOR_THEATRE", "PENDING", false);
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.startProcedure(episodeId, Map.of()));
        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("consent"));
    }
}
