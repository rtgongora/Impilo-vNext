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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pipeline demonstration 10 (V305) — a return to theatre is a recorded clinical event.
 *
 * <p>The demonstration's own premise needed correcting first: the traceability docs recorded
 * {@code procedure_episode} as unable to reopen at all, and it has been able to since Wave 4 —
 * the case stays on the SAME episode and goes back to READY_FOR_THEATRE. What was actually
 * missing was everything about the return, and that is what these assertions are about: why, who,
 * how many times, planned or not, and which operative note it followed from.</p>
 *
 * <p>The distinction that matters most here is planned versus unplanned. The DAK safety indicator
 * counts UNPLANNED returns; a staged second-look laparotomy booked at the first operation is
 * planned care. One boolean could not tell them apart, so the indicator was counting good practice
 * as harm.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProcedureReturnToTheatreTest {

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
        lenient().when(postopRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(returnToTheatreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(procedureConsentRepository.findByEpisodeId(episodeId)).thenReturn(java.util.List.of());
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void givenEpisodeIn(String status) {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-1");
        e.setProcedureName("Laparotomy");
        e.setStatus(status);
        lenient().when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private ProcedureReturnToTheatreEntity captureSavedReturn() {
        ArgumentCaptor<ProcedureReturnToTheatreEntity> captor =
                ArgumentCaptor.forClass(ProcedureReturnToTheatreEntity.class);
        verify(returnToTheatreRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a return records why, who and when — not just that it happened")
    void returnIsRecordedInFull() {
        givenEpisodeIn("PACU");

        service.returnToTheatre(episodeId, body(
                "reason", "Post-op intra-abdominal bleeding, falling haemoglobin",
                "complicationCategory", "HAEMORRHAGE"));

        ProcedureReturnToTheatreEntity saved = captureSavedReturn();
        assertEquals(episodeId, saved.getEpisodeId());
        assertEquals(1, saved.getSeq());
        assertEquals("HAEMORRHAGE", saved.getComplicationCategory());
        assertEquals("Post-op intra-abdominal bleeding, falling haemoglobin", saved.getReason());
        assertEquals("actor-surgeon", saved.getReturnedBy(), "the deciding actor comes from the trust context");
        assertFalse(saved.isPlanned(), "an unclassified return counts as unplanned — for a safety "
                + "indicator, over-reporting is the correct direction to be wrong in");
    }

    @Test
    @DisplayName("the case keeps its own episode and goes back to READY_FOR_THEATRE")
    void returnKeepsTheSameEpisode() {
        givenEpisodeIn("PACU");

        Map<String, Object> detail = service.returnToTheatre(episodeId, body(
                "reason", "Anastomotic leak", "complicationCategory", "ANASTOMOTIC_LEAK"));

        assertEquals("READY_FOR_THEATRE", detail.get("status"));
        assertEquals(episodeId.toString(), detail.get("id"),
                "the returning case is the same case — a new episode here would break the continuity "
                        + "demonstration 10 exists to prove");
    }

    /** The V031 boolean stays true, so the analytics rollup and the alt-journeys rig keep working. */
    @Test
    @DisplayName("the existing postop flag is still maintained alongside the new record")
    void postopFlagStillSet() {
        givenEpisodeIn("RECOVERED");

        service.returnToTheatre(episodeId, body("reason", "Sepsis", "complicationCategory", "SEPSIS"));

        ArgumentCaptor<ProcedurePostopRecordEntity> captor =
                ArgumentCaptor.forClass(ProcedurePostopRecordEntity.class);
        verify(postopRepository).save(captor.capture());
        assertTrue(captor.getValue().isReturnToTheatre());
    }

    @Test
    @DisplayName("a second return is the second return, not a repeat of the first")
    void secondReturnIsCounted() {
        givenEpisodeIn("PACU");
        when(returnToTheatreRepository.countByEpisodeId(episodeId)).thenReturn(1L);

        service.returnToTheatre(episodeId, body(
                "reason", "Further washout for persistent contamination", "complicationCategory", "SEPSIS"));

        assertEquals(2, captureSavedReturn().getSeq(),
                "a boolean cannot count; the second washout must not vanish from the record");
    }

    @Test
    @DisplayName("the return links back to the operative note it followed from")
    void originatingNoteIsLinked() {
        givenEpisodeIn("PACU");
        ProcedureNoteEntity note = new ProcedureNoteEntity();
        UUID noteId = UUID.randomUUID();
        note.setNoteId(noteId);
        note.setEpisodeId(episodeId);
        note.setComplications("Oozing from the gallbladder bed at closure");
        when(noteRepository.findByEpisodeId(episodeId)).thenReturn(Optional.of(note));

        service.returnToTheatre(episodeId, body(
                "reason", "Continued bleeding from the gallbladder bed", "complicationCategory", "HAEMORRHAGE"));

        assertEquals(noteId, captureSavedReturn().getOriginatingNoteId());
    }

    @Test
    @DisplayName("a ward deterioration with no operative note is still recorded, with no invented link")
    void missingNoteLeavesTheLinkNull() {
        givenEpisodeIn("RECOVERED");
        when(noteRepository.findByEpisodeId(episodeId)).thenReturn(Optional.empty());

        service.returnToTheatre(episodeId, body(
                "reason", "Deterioration noticed on the ward", "complicationCategory", "OTHER"));

        assertNull(captureSavedReturn().getOriginatingNoteId(),
                "no note is not an error and must not become a fabricated reference");
    }

    @Test
    @DisplayName("a return with no reason is refused")
    void reasonIsRequired() {
        givenEpisodeIn("PACU");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.returnToTheatre(episodeId, body("complicationCategory", "SEPSIS")));

        assertEquals(400, ex.getStatusCode().value());
        verify(returnToTheatreRepository, never()).save(any());
        verify(episodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("a return with no complication category is refused")
    void categoryIsRequired() {
        givenEpisodeIn("PACU");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.returnToTheatre(episodeId, body("reason", "Something went wrong")));

        assertEquals(400, ex.getStatusCode().value());
        verify(returnToTheatreRepository, never()).save(any());
    }

    @Test
    @DisplayName("an invented complication category is refused rather than stored as free text")
    void inventedCategoryIsRefused() {
        givenEpisodeIn("PACU");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.returnToTheatre(episodeId,
                        body("reason", "Bleeding", "complicationCategory", "A_BIT_OF_BLEEDING")));

        assertEquals(400, ex.getStatusCode().value());
        verify(returnToTheatreRepository, never()).save(any());
    }

    @Test
    @DisplayName("a planned second-look is recorded as planned, so it does not count as harm")
    void plannedSecondLookIsPlanned() {
        givenEpisodeIn("PACU");

        service.returnToTheatre(episodeId, body(
                "reason", "Staged second-look laparotomy booked at the index operation",
                "complicationCategory", "PLANNED_SECOND_LOOK",
                "planned", true));

        assertTrue(captureSavedReturn().isPlanned());
    }

    @Test
    @DisplayName("a second-look that claims to be unplanned is a contradiction and is refused")
    void plannedSecondLookMarkedUnplannedIsRefused() {
        givenEpisodeIn("PACU");

        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.returnToTheatre(episodeId, body(
                        "reason", "Second look", "complicationCategory", "PLANNED_SECOND_LOOK")))
                .getStatusCode().value());
        verify(returnToTheatreRepository, never()).save(any());
    }

    /**
     * The dangerous direction: marking a haemorrhage as planned would remove it from the unplanned
     * return count, which is the one number the indicator exists to watch.
     */
    @Test
    @DisplayName("a haemorrhage cannot be declared planned and quietly leave the harm count")
    void complicationMarkedPlannedIsRefused() {
        givenEpisodeIn("PACU");

        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.returnToTheatre(episodeId, body(
                        "reason", "Bleeding", "complicationCategory", "HAEMORRHAGE", "planned", true)))
                .getStatusCode().value());
        verify(returnToTheatreRepository, never()).save(any());
    }

    @Test
    @DisplayName("a case that never reached recovery cannot return to theatre")
    void wrongStatusIsRefused() {
        givenEpisodeIn("BOOKED");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.returnToTheatre(episodeId,
                        body("reason", "Bleeding", "complicationCategory", "HAEMORRHAGE")));

        assertEquals(409, ex.getStatusCode().value());
        verify(returnToTheatreRepository, never()).save(any());
    }
}
