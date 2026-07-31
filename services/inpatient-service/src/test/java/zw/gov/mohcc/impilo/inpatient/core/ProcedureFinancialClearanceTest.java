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
import zw.gov.mohcc.impilo.inpatient.integration.CostaServiceAccessClient.ClearanceResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Wave P12 — pipeline §23. The audit found "estimate/authorisation/denial/appeal absent" (they
 * were not — coverage-service and COSTA already have that machinery) and "the emergency-rules
 * invariant unproven" (it genuinely was: TheatreService/ProcedureEpisodeService never referenced
 * COSTA, payment or authorisation at all before this wave). These tests prove the invariant is
 * now enforced, not merely asserted: an emergency episode NEVER queries COSTA and NEVER blocks;
 * an elective episode is asked fresh and blocked ONLY on an explicit BLOCKED_* status; and a
 * financial block never transitions the episode to CANCELLED — it only refuses the start
 * attempt, leaving the episode retryable.
 */
@ExtendWith(MockitoExtension.class)
class ProcedureFinancialClearanceTest {

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
    @Mock zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureReturnToTheatreRepository returnToTheatreRepository;
    @Mock zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureNoteRepository noteRepository;

    private ProcedureEpisodeService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();
    private final UUID encounterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProcedureEpisodeService(episodeRepository, preopRepository, checklistRepository,
                intraopRepository, postopRepository, pacuObservationRepository, documentRepository, consumableRepository,
                implantRepository, anaesthesiaScoreRepository, countRepository, chartRepository, bloodLinkRepository,
                outboxRepository, procedureConsentRepository, new ObjectMapper(), costaServiceAccessClient,
                returnToTheatreRepository, noteRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(procedureConsentRepository.findByEpisodeId(episodeId)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    /** Ordinary, fully-consented episode ready for the financial gate to be the deciding factor. */
    private ProcedureEpisodeEntity electiveEpisode() {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-FIN-1");
        e.setStatus("READY_FOR_THEATRE");
        e.setTriagePriority("ELECTIVE");
        e.setEncounterId(encounterId);
        e.setConsentStatus("GRANTED");
        e.setMvumoConsentRequestId(UUID.randomUUID());
        return e;
    }

    @Test
    void anEmergencyEpisodeNeverQueriesCostaAndIsRecordedAsEmergencyOverride() {
        ProcedureEpisodeEntity e = electiveEpisode();
        e.setTriagePriority("EMERGENCY");
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));

        service.startProcedure(episodeId, Map.of());

        assertEquals("IN_PROGRESS", e.getStatus());
        assertEquals("EMERGENCY_OVERRIDE", e.getFinancialClearanceStatus());
        assertNotNull(e.getFinancialClearanceCheckedAt());
        verify(costaServiceAccessClient, never()).checkClearance(anyString());
    }

    @Test
    void anImmediateTriagePriorityAlsoBypassesTheGate() {
        ProcedureEpisodeEntity e = electiveEpisode();
        e.setTriagePriority("IMMEDIATE");
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));

        service.startProcedure(episodeId, Map.of());

        assertEquals("IN_PROGRESS", e.getStatus());
        verify(costaServiceAccessClient, never()).checkClearance(anyString());
    }

    @Test
    void anElectiveEpisodeWithNoCostaDecisionIsNotBlocked() {
        ProcedureEpisodeEntity e = electiveEpisode();
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        when(costaServiceAccessClient.checkClearance(encounterId.toString()))
                .thenReturn(ClearanceResult.notYetEvaluated());

        service.startProcedure(episodeId, Map.of());

        assertEquals("IN_PROGRESS", e.getStatus());
        assertEquals("NOT_YET_EVALUATED", e.getFinancialClearanceStatus());
    }

    @Test
    void anElectiveEpisodeWithCostaUnreachableIsNotBlocked() {
        ProcedureEpisodeEntity e = electiveEpisode();
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        when(costaServiceAccessClient.checkClearance(encounterId.toString()))
                .thenReturn(ClearanceResult.unavailable());

        service.startProcedure(episodeId, Map.of());

        assertEquals("IN_PROGRESS", e.getStatus(), "COSTA being unreachable must never block theatre start");
    }

    @Test
    void anElectiveEpisodeAllowedWithoutPaymentIsNotBlocked() {
        ProcedureEpisodeEntity e = electiveEpisode();
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        when(costaServiceAccessClient.checkClearance(encounterId.toString()))
                .thenReturn(ClearanceResult.of("ALLOWED_WITHOUT_PAYMENT"));

        service.startProcedure(episodeId, Map.of());

        assertEquals("IN_PROGRESS", e.getStatus());
    }

    @Test
    void anElectiveEpisodeBlockedPendingPaymentRefusesTheStart() {
        ProcedureEpisodeEntity e = electiveEpisode();
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        when(costaServiceAccessClient.checkClearance(encounterId.toString()))
                .thenReturn(ClearanceResult.of("BLOCKED_PENDING_PAYMENT"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.startProcedure(episodeId, Map.of()));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("BLOCKED_PENDING_PAYMENT"));
    }

    @Test
    void anElectiveEpisodeBlockedPendingAuthorisationRefusesTheStart() {
        ProcedureEpisodeEntity e = electiveEpisode();
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        when(costaServiceAccessClient.checkClearance(encounterId.toString()))
                .thenReturn(ClearanceResult.of("BLOCKED_PENDING_AUTHORISATION"));

        assertThrows(ResponseStatusException.class, () -> service.startProcedure(episodeId, Map.of()));
    }

    /**
     * The other half of the invariant, proven directly rather than inferred: a financial block
     * NEVER masquerades as a clinical cancellation. The episode stays exactly where it was.
     */
    @Test
    void aFinancialBlockNeverCancelsTheEpisode() {
        ProcedureEpisodeEntity e = electiveEpisode();
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        when(costaServiceAccessClient.checkClearance(encounterId.toString()))
                .thenReturn(ClearanceResult.of("BLOCKED_PENDING_PAYMENT"));

        assertThrows(ResponseStatusException.class, () -> service.startProcedure(episodeId, Map.of()));

        assertEquals("READY_FOR_THEATRE", e.getStatus(), "a financial block must never cancel the episode");
        assertNotEquals("CANCELLED", e.getStatus());
    }

    /** Financial clearance runs LAST — an unrelated, pre-existing gate failure is reported first. */
    @Test
    void anUnconsentedElectiveEpisodeFailsOnConsentBeforeCostaIsEverAsked() {
        ProcedureEpisodeEntity e = electiveEpisode();
        e.setConsentStatus("PENDING");
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.startProcedure(episodeId, Map.of()));

        assertTrue(ex.getReason().contains("consent"));
        verify(costaServiceAccessClient, never()).checkClearance(anyString());
    }

    /** The financial-clearance check is asked fresh every time, never trusting a stale projection. */
    @Test
    void financialClearanceIsQueriedFreshNotReadFromAPriorProjection() {
        ProcedureEpisodeEntity e = electiveEpisode();
        e.setFinancialClearanceStatus("BLOCKED_PENDING_PAYMENT");
        e.setFinancialClearanceCheckedBy("someone-earlier");
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        when(costaServiceAccessClient.checkClearance(encounterId.toString()))
                .thenReturn(ClearanceResult.of("ALLOWED_WITHOUT_PAYMENT"));

        service.startProcedure(episodeId, Map.of());

        assertEquals("IN_PROGRESS", e.getStatus());
        assertEquals("ALLOWED_WITHOUT_PAYMENT", e.getFinancialClearanceStatus());
        verify(costaServiceAccessClient, times(1)).checkClearance(encounterId.toString());
    }
}
