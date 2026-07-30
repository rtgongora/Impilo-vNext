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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Wave 1: WHO Sign-Out surgical-count gate + multi-channel anaesthesia chart (ordering + blood projection). */
@ExtendWith(MockitoExtension.class)
class ProcedureAnaesthesiaChartAndCountGateTest {

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

    @BeforeEach
    void setUp() {
        service = new ProcedureEpisodeService(episodeRepository, preopRepository, checklistRepository,
                intraopRepository, postopRepository, pacuObservationRepository, documentRepository, consumableRepository,
                implantRepository, anaesthesiaScoreRepository, countRepository, chartRepository, bloodLinkRepository,
                outboxRepository, procedureConsentRepository, new ObjectMapper(), costaServiceAccessClient,
                returnToTheatreRepository, noteRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-anaesthetist", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-1");
        lenient().when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    private ProcedureCountEntity unreconciled() {
        ProcedureCountEntity c = new ProcedureCountEntity();
        c.setCountId(UUID.randomUUID());
        c.setEpisodeId(episodeId);
        c.setKind("SWAB");
        c.setDiscrepancy(1);
        c.setReconciled(false);
        return c;
    }

    @Test
    void signOut_blockedByUnreconciledCount() {
        when(countRepository.findByEpisodeIdAndReconciledFalseAndOverrideFalse(episodeId))
                .thenReturn(List.of(unreconciled()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.completeChecklistItems(episodeId, "SIGN_OUT", Map.of("itemIds", List.of())));

        assertTrue(ex.getStatusCode().is4xxClientError());
        assertTrue(ex.getReason().contains("Sign-Out"));
        // Gate fired before any checklist mutation.
        verify(checklistRepository, never()).save(any());
    }

    @Test
    void signOut_proceedsWithExplicitCountOverrideReason() {
        when(countRepository.findByEpisodeIdAndReconciledFalseAndOverrideFalse(episodeId))
                .thenReturn(List.of(unreconciled()));
        when(checklistRepository.findByEpisodeIdOrderByPhaseAscItemCodeAsc(episodeId)).thenReturn(List.of());
        when(checklistRepository.findByEpisodeIdAndPhaseOrderByItemCodeAsc(any(), any())).thenReturn(List.of());

        Map<String, Object> result = service.completeChecklistItems(episodeId, "SIGN_OUT",
                Map.of("itemIds", List.of(), "countOverrideReason", "Imaging confirmed no retained swab"));

        assertEquals("SIGN_OUT", result.get("phase"));
        // The outstanding count is overridden (with the reason) for audit.
        verify(countRepository, atLeastOnce()).save(argThat(c ->
                ((ProcedureCountEntity) c).isOverride()
                        && "Imaging confirmed no retained swab".equals(((ProcedureCountEntity) c).getOverrideReason())));
    }

    @Test
    void signOut_passesWhenAllCountsReconciled() {
        when(countRepository.findByEpisodeIdAndReconciledFalseAndOverrideFalse(episodeId)).thenReturn(List.of());
        when(checklistRepository.findByEpisodeIdOrderByPhaseAscItemCodeAsc(episodeId)).thenReturn(List.of());
        when(checklistRepository.findByEpisodeIdAndPhaseOrderByItemCodeAsc(any(), any())).thenReturn(List.of());

        assertDoesNotThrow(() ->
                service.completeChecklistItems(episodeId, "SIGN_OUT", Map.of("itemIds", List.of())));
    }

    @Test
    void recordChartEntry_persistsAndEmitsCharted() {
        when(chartRepository.countByEpisodeIdAndChannel(episodeId, "AGENT")).thenReturn(0);
        when(chartRepository.save(any())).thenAnswer(i -> {
            ProcedureAnaesthesiaChartEntryEntity e = i.getArgument(0);
            if (e.getEntryId() == null) e.setEntryId(UUID.randomUUID());
            return e;
        });

        Map<String, Object> row = service.recordChartEntry(episodeId,
                Map.of("channel", "AGENT", "technique", "GA", "label", "Sevoflurane", "valueNumeric", "2.0", "unit", "%"));

        assertEquals("AGENT", row.get("channel"));
        assertEquals("GA", row.get("technique"));
        assertEquals(1, row.get("seq"));
        verify(outboxRepository).save(argThat(o ->
                "theatre.anaesthesia.charted".equals(((EventOutboxEntity) o).getEventType())));
    }

    @Test
    void getAnaesthesiaChart_returnsOrderedSeriesWithProjectedBloodChannel() {
        OffsetDateTime t0 = OffsetDateTime.now().minusMinutes(10);
        ProcedureAnaesthesiaChartEntryEntity vital = new ProcedureAnaesthesiaChartEntryEntity();
        vital.setEntryId(UUID.randomUUID());
        vital.setEpisodeId(episodeId);
        vital.setChannel("VITAL");
        vital.setRecordedAt(t0);
        when(chartRepository.findByEpisodeIdOrderByRecordedAtAscChannelAsc(episodeId)).thenReturn(List.of(vital));

        ProcedureBloodLinkEntity link = new ProcedureBloodLinkEntity();
        link.setLinkId(UUID.randomUUID());
        link.setEpisodeId(episodeId);
        link.setMadiTransfusionEpisodeId("MADI-TX-1");
        link.setComponentType("PRBC");
        link.setStatus("TRANSFUSED");
        link.setUpdatedAt(OffsetDateTime.now());
        when(bloodLinkRepository.findByEpisodeIdOrderByCreatedAtAsc(episodeId)).thenReturn(List.of(link));

        Map<String, Object> chart = service.getAnaesthesiaChart(episodeId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) chart.get("entries");

        assertEquals(2, entries.size(), "persisted VITAL + projected BLOOD");
        // Ordered by recorded_at: VITAL (t0) before projected BLOOD (now).
        assertEquals("VITAL", entries.get(0).get("channel"));
        assertEquals("BLOOD", entries.get(1).get("channel"));
        assertEquals(Boolean.TRUE, entries.get(1).get("projected"));
        assertEquals("MADI-TX-1", entries.get(1).get("madi_transfusion_episode_id"));
    }
}
