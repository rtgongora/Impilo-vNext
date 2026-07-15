package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
 * Wave 5b §15 — trauma↔theatre link CONSUMPTION at the service layer. An emergency-surgery / C-section
 * intake reads the inbound {@code X-Trauma-Episode-ID} (the daidzai/PCT-minted UUID, carried as its
 * canonical string) and stamps it onto {@code procedure_episode.trauma_episode_id} — ONE coherent
 * trauma↔theatre episode, no parallel surgery record. The id is CONSUMED, never minted. Elective intake
 * (no header) leaves the column null. The trauma id is echoed into the theatre outbox events so the
 * daidzai episode-timeline consumer correlates the theatre phase.
 */
@ExtendWith(MockitoExtension.class)
class TheatreTraumaLinkTest {

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

    /** Wire the intake pipeline: no pre-existing case for the order, createEpisode yields a fresh entity. */
    private ProcedureEpisodeEntity primeIntake(String orosOrderId) {
        UUID episodeId = UUID.randomUUID();
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-ZW-TRAUMA");
        e.setProcedureName("Emergency laparotomy");
        e.setStatus("BOOKED");
        e.setTriagePriority("ELECTIVE");
        when(episodeRepository.findByTenantIdAndOrosOrderId(tenant, orosOrderId)).thenReturn(Optional.empty());
        when(episodeService.createEpisode(anyMap()))
                .thenReturn(new LinkedHashMap<>(Map.of("id", episodeId.toString())));
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(e));
        when(episodeService.getEpisode(episodeId)).thenReturn(new LinkedHashMap<>(Map.of("id", episodeId.toString())));
        return e;
    }

    @Test
    void intakeStampsTraumaEpisodeIdFromHeaderWhenPresent() {
        String oros = "RIG-ORDER-A";
        ProcedureEpisodeEntity e = primeIntake(oros);
        UUID trauma = UUID.randomUUID();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patientId", "CPID-ZW-TRAUMA");
        body.put("procedureName", "Emergency laparotomy");
        body.put("orosOrderId", oros);
        // Controller folds X-Trauma-Episode-ID into the body as the UUID canonical string.
        body.put("traumaEpisodeId", trauma.toString());

        service.intakeFromOrosOrder(body);

        assertEquals(trauma, e.getTraumaEpisodeId(),
                "trauma_episode_id must be CONSUMED from the header and stamped onto the episode");

        // The intake outbox event carries the trauma_episode_id for the daidzai timeline consumer.
        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertTrue(outbox.getValue().getPayloadJson().contains(trauma.toString()),
                "intake event payload must echo the trauma_episode_id");
    }

    @Test
    void intakeLeavesTraumaEpisodeIdNullForElectiveWithoutHeader() {
        String oros = "RIG-ORDER-B";
        ProcedureEpisodeEntity e = primeIntake(oros);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patientId", "CPID-ZW-ELECTIVE");
        body.put("procedureName", "Elective cholecystectomy");
        body.put("orosOrderId", oros);
        // No X-Trauma-Episode-ID → no traumaEpisodeId in the body.

        service.intakeFromOrosOrder(body);

        assertNull(e.getTraumaEpisodeId(), "elective intake must NOT invent a trauma episode id");
    }

    @Test
    void intakeAcceptsAProvisionalCpidUnchanged() {
        // A trauma patient may arrive unidentified: the provisional identity (CPID) is minted upstream in
        // VITO; intake accepts whatever patient_cpid it is given, including a PROVISIONAL one.
        String oros = "RIG-ORDER-C";
        ProcedureEpisodeEntity e = primeIntake(oros);
        UUID trauma = UUID.randomUUID();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", "CPID-PROVISIONAL-UNKNOWN");
        body.put("procedureName", "Emergency thoracotomy");
        body.put("orosOrderId", oros);
        body.put("trauma_episode_id", trauma.toString());

        // Does not throw on a provisional CPID; the trauma link is still stamped.
        service.intakeFromOrosOrder(body);
        assertEquals(trauma, e.getTraumaEpisodeId());
    }
}
