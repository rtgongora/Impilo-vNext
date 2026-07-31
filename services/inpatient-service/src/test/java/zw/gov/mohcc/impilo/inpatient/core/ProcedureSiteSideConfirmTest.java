package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.integration.CostaServiceAccessClient;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.*;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Wave B3 — table-side confirmation of marked site and laterality on a procedure episode.
 */
@ExtendWith(MockitoExtension.class)
class ProcedureSiteSideConfirmTest {

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
    @Mock ProcedureReturnToTheatreRepository returnToTheatreRepository;
    @Mock ProcedureNoteRepository noteRepository;

    private ProcedureEpisodeService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProcedureEpisodeService(episodeRepository, preopRepository, checklistRepository,
                intraopRepository, postopRepository, pacuObservationRepository, documentRepository,
                consumableRepository, implantRepository, anaesthesiaScoreRepository, countRepository,
                chartRepository, bloodLinkRepository, outboxRepository, procedureConsentRepository,
                new ObjectMapper(), costaServiceAccessClient, returnToTheatreRepository, noteRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(checklistRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(procedureConsentRepository.findByEpisodeId(episodeId)).thenReturn(List.of());
        lenient().when(preopRepository.findByEpisodeIdOrderByAssessedAtAsc(episodeId)).thenReturn(List.of());
        lenient().when(checklistRepository.findByEpisodeIdOrderByPhaseAscItemCodeAsc(episodeId)).thenReturn(List.of());
        lenient().when(intraopRepository.findByEpisodeIdOrderByRecordedAtAsc(episodeId)).thenReturn(List.of());
        lenient().when(postopRepository.findByEpisodeId(episodeId)).thenReturn(Optional.empty());
        lenient().when(pacuObservationRepository.findByEpisodeIdOrderBySeqAsc(episodeId)).thenReturn(List.of());
        lenient().when(documentRepository.findByEpisodeIdOrderByRecordedAtDesc(episodeId)).thenReturn(List.of());
        lenient().when(consumableRepository.findByEpisodeIdOrderByRecordedAtDesc(episodeId)).thenReturn(List.of());
        lenient().when(anaesthesiaScoreRepository.findByEpisodeIdOrderByRecordedAtDesc(episodeId)).thenReturn(List.of());
        lenient().when(returnToTheatreRepository.findByEpisodeIdOrderBySeqAsc(episodeId)).thenReturn(List.of());
        lenient().when(checklistRepository.findByEpisodeIdAndPhaseOrderByItemCodeAsc(episodeId, "SIGN_IN"))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private ProcedureEpisodeEntity givenEpisode() {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-1");
        e.setProcedureName("Left inguinal hernia repair");
        e.setStatus("PREOP");
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        return e;
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("laterality required — blank body is rejected")
    void rejectsWithoutLaterality() {
        givenEpisode();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirmSiteAndSide(episodeId, body("anatomicalSite", "Left groin")));
        assertEquals(400, ex.getStatusCode().value());
        verify(episodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("laterality closed vocabulary — free text rejected")
    void rejectsUnknownLaterality() {
        givenEpisode();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirmSiteAndSide(episodeId, body("laterality", "leftish")));
        assertEquals(400, ex.getStatusCode().value());
        verify(episodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirm writes laterality, site, actor and timestamp; projects banner aliases")
    void confirmsSiteAndSide() {
        ProcedureEpisodeEntity e = givenEpisode();
        Map<String, Object> detail = service.confirmSiteAndSide(episodeId, body(
                "laterality", "LEFT",
                "anatomicalSite", "Left inguinal canal"));

        ArgumentCaptor<ProcedureEpisodeEntity> captor = ArgumentCaptor.forClass(ProcedureEpisodeEntity.class);
        verify(episodeRepository).save(captor.capture());
        ProcedureEpisodeEntity saved = captor.getValue();
        assertEquals("LEFT", saved.getLaterality());
        assertEquals("Left inguinal canal", saved.getAnatomicalSite());
        assertEquals("actor-surgeon", saved.getSiteSideConfirmedBy());
        assertNotNull(saved.getSiteSideConfirmedAt());

        assertEquals("LEFT", detail.get("laterality"));
        assertEquals("Left inguinal canal", detail.get("anatomical_site"));
        assertEquals("LEFT", detail.get("surgical_side"));
        assertEquals("Left inguinal canal", detail.get("surgical_site"));
        assertEquals("actor-surgeon", detail.get("site_side_confirmed_by"));
        assertNotNull(detail.get("site_side_confirmed_at"));
        assertSame(e, saved);
    }

    @Test
    @DisplayName("successful confirm optionally completes WHO SITE_MARKED checklist item")
    void completesSiteMarkedChecklist() {
        givenEpisode();
        ProcedureChecklistItemEntity item = new ProcedureChecklistItemEntity();
        item.setItemId(UUID.randomUUID());
        item.setEpisodeId(episodeId);
        item.setPhase("SIGN_IN");
        item.setItemCode("SITE_MARKED");
        item.setItemLabel("Procedure site marked");
        item.setCompleted(false);
        when(checklistRepository.findByEpisodeIdAndPhaseOrderByItemCodeAsc(episodeId, "SIGN_IN"))
                .thenReturn(List.of(item));
        lenient().when(preopRepository.findByEpisodeIdAndAssessmentType(eq(episodeId), anyString()))
                .thenReturn(Optional.empty());

        service.confirmSiteAndSide(episodeId, body("laterality", "RIGHT", "anatomicalSite", "Right knee"));

        assertTrue(item.isCompleted());
        assertEquals("actor-surgeon", item.getCompletedBy());
        assertNotNull(item.getCompletedAt());
        verify(checklistRepository).save(item);
    }
}
