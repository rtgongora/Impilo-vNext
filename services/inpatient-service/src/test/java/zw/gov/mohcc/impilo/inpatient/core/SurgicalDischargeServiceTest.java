package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inpatient.integration.BookingFollowUpClient;
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

/** Wave 4 §14 — surgical discharge specialisation: pending-histopath survival + follow-up booking + Butano. */
@ExtendWith(MockitoExtension.class)
class SurgicalDischargeServiceTest {

    @Mock ProcedureDischargeRepository dischargeRepository;
    @Mock ProcedureEpisodeRepository episodeRepository;
    @Mock ProcedureNoteRepository noteRepository;
    @Mock ProcedureSpecimenRepository specimenRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock BookingFollowUpClient bookingFollowUpClient;

    private SurgicalDischargeService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SurgicalDischargeService(dischargeRepository, episodeRepository, noteRepository,
                specimenRepository, outboxRepository, bookingFollowUpClient, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-7");
        e.setProcedureName("Cholecystectomy");
        lenient().when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        lenient().when(dischargeRepository.save(any())).thenAnswer(i -> {
            ProcedureDischargeEntity d = i.getArgument(0);
            if (d.getDischargeId() == null) d.setDischargeId(UUID.randomUUID());
            return d;
        });
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    private ProcedureSpecimenEntity specimen(String label, String status) {
        ProcedureSpecimenEntity s = new ProcedureSpecimenEntity();
        s.setSpecimenId(UUID.randomUUID());
        s.setEpisodeId(episodeId);
        s.setSpecimenLabel(label);
        s.setSpecimenType("HISTOPATH");
        s.setStatus(status);
        return s;
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeSurfacesPendingHistopathAndBooksFollowUpAndPublishesButano() {
        when(dischargeRepository.findByEpisodeId(episodeId)).thenReturn(Optional.empty());
        ProcedureNoteEntity note = new ProcedureNoteEntity();
        note.setPerformedProcedure("Laparoscopic cholecystectomy");
        note.setFindings("Inflamed gallbladder");
        note.setComplications("None");
        when(noteRepository.findByEpisodeId(episodeId)).thenReturn(Optional.of(note));
        // One pending (COLLECTED) + one terminal (RESULTED) — only the pending should survive on the summary.
        when(specimenRepository.findByEpisodeIdOrderBySeqAsc(episodeId))
                .thenReturn(List.of(specimen("Gallbladder", "COLLECTED"), specimen("Node", "RESULTED")));
        when(bookingFollowUpClient.createFollowUp(eq("CPID-7"), any(), any(), eq(false), any(), any()))
                .thenReturn("BOOK-1");

        Map<String, Object> out = service.complete(episodeId, Map.of(
                "finalDiagnosis", "Cholelithiasis",
                "followUpDate", "2026-08-01",
                "followUpService", "General surgery OPD"));

        assertEquals("COMPLETED", out.get("status"));
        // Procedure summary composed from the operative note.
        assertTrue(String.valueOf(out.get("procedure_summary")).contains("cholecystectomy"));
        // Pending histopath survives discharge and is surfaced (exactly the non-terminal specimen).
        List<Map<String, Object>> pending = (List<Map<String, Object>>) out.get("pending_pathology");
        assertEquals(1, pending.size());
        assertEquals("Gallbladder", pending.get(0).get("label"));
        // Follow-up became a real booking.
        assertEquals("BOOK-1", out.get("follow_up_booking_ref"));
        // Butano publication + case-completed events emitted.
        verify(outboxRepository, times(2)).save(any());
        verify(bookingFollowUpClient).createFollowUp(eq("CPID-7"), any(), any(), eq(false), any(), any());
    }

    @Test
    void teleconsultFollowUpBooksVideoChannel() {
        when(dischargeRepository.findByEpisodeId(episodeId)).thenReturn(Optional.empty());
        when(noteRepository.findByEpisodeId(episodeId)).thenReturn(Optional.empty());
        when(specimenRepository.findByEpisodeIdOrderBySeqAsc(episodeId)).thenReturn(List.of());
        when(bookingFollowUpClient.createFollowUp(eq("CPID-7"), any(), any(), eq(true), any(), any()))
                .thenReturn("TELE-BOOK-1");

        Map<String, Object> out = service.complete(episodeId, Map.of(
                "followUpDate", "2026-08-05", "followUpIsTeleconsult", true));

        assertEquals("TELE-BOOK-1", out.get("follow_up_booking_ref"));
        assertEquals(true, out.get("follow_up_is_teleconsult"));
        verify(bookingFollowUpClient).createFollowUp(eq("CPID-7"), any(), any(), eq(true), any(), any());
    }
}
