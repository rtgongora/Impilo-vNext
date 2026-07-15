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
 * Wave 5b §15 — OBSTETRIC EMERGENCY C-SECTION. Maternal + fetal context is recorded on the ONE mother
 * episode; the neonatal team is paged (by reference); the baby is delivered under a PROVISIONAL identity
 * minted upstream in VITO and LINKED to the mother's episode (never minting identity here); the neonate
 * is handed to a postnatal/neonatal destination. Identity is REFERENCED (VITO owns it), not duplicated.
 */
@ExtendWith(MockitoExtension.class)
class TheatreObstetricCSectionTest {

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

    @BeforeEach
    void setUp() {
        service = new TheatreService(episodeRepository, readinessRepository, noteRepository, safetyRepository,
                checklistRepository, outboxRepository, episodeService, orosOrderClient, readinessClient,
                deathClient, butanoClient, bloodLinkRepository, transportRepository, specimenRepository,
                countRepository, madiBloodClient, nhumeTransportClient, orosSpecimenClient, ritoSafetyClient,
                postopRepository, admissionService, teleconsultLinkRepository, pctTeleconsultClient,
                fundoSurgicalLogbookClient, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(tenant, "actor-obstetrician", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    private ProcedureEpisodeEntity motherEpisode(UUID trauma) {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-MOTHER");
        e.setProcedureName("Emergency caesarean section");
        e.setStatus("IN_PROGRESS");
        e.setTraumaEpisodeId(trauma);
        return e;
    }

    @Test
    void obstetricContextRecordsMaternalAndFetalIntraopEvents() {
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(motherEpisode(null)));
        when(episodeService.getEpisode(episodeId)).thenReturn(new LinkedHashMap<>());

        Map<String, Object> out = service.recordObstetricContext(episodeId, Map.of(
                "gravida", 3, "para", 2, "gestationWeeks", 38, "indication", "Fetal distress",
                "fetalPresentation", "CEPHALIC", "fetalHeartRate", 90, "fetalCount", 1, "urgency", "CATEGORY_1"));

        // Maternal + fetal recorded as two distinct episode-scoped intra-op events.
        verify(episodeService).recordIntraopEvent(eq(episodeId),
                argThat(m -> "MATERNAL_CONTEXT".equals(m.get("eventType"))));
        verify(episodeService).recordIntraopEvent(eq(episodeId),
                argThat(m -> "FETAL_CONTEXT".equals(m.get("eventType"))));
        assertNotNull(out.get("maternal_context"));
        assertNotNull(out.get("fetal_context"));
    }

    @Test
    void neonatalAlertPagesTheTeamByReference() {
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(motherEpisode(null)));

        Map<String, Object> out = service.notifyNeonatalTeam(episodeId, Map.of(
                "urgency", "CATEGORY_1", "reason", "Cord prolapse", "expectedNeonates", 2));

        assertEquals("PAGED", out.get("neonatal_team"));
        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals("theatre.obstetric.neonatal-alert", outbox.getValue().getEventType());
    }

    @Test
    void deliveryLinksProvisionalBabyToMotherAndCorrelatesTrauma() {
        UUID trauma = UUID.randomUUID();
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(motherEpisode(trauma)));

        Map<String, Object> out = service.recordDelivery(episodeId, Map.of(
                "babyCpid", "CPID-PROVISIONAL-BABY", "sex", "F", "apgar1", 7, "apgar5", 9, "outcome", "LIVE_BIRTH"));

        assertEquals("CPID-MOTHER", out.get("mother_cpid"));
        assertEquals("CPID-PROVISIONAL-BABY", out.get("baby_cpid"));
        // The delivery is stored as a mother-episode-scoped DELIVERY intra-op event (no separate record).
        verify(episodeService).recordIntraopEvent(eq(episodeId),
                argThat(m -> "DELIVERY".equals(m.get("eventType"))));
        // The delivery event carries baby CPID + the shared trauma_episode_id for correlation.
        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        String payload = outbox.getValue().getPayloadJson();
        assertTrue(payload.contains("CPID-PROVISIONAL-BABY"));
        assertTrue(payload.contains(trauma.toString()), "delivery event must correlate to the trauma episode");
    }

    @Test
    void deliveryRequiresABabyCpid() {
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(motherEpisode(null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.recordDelivery(episodeId, Map.of("sex", "M")));
        assertEquals(400, ex.getStatusCode().value());
        verify(episodeService, never()).recordIntraopEvent(any(), any());
    }

    @Test
    void neonatalHandoverRoutesBabyToPostnatalOrNeonatal() {
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(motherEpisode(null)));

        Map<String, Object> nicu = service.recordNeonatalHandover(episodeId, Map.of(
                "babyCpid", "CPID-PROVISIONAL-BABY", "destination", "nicu"));
        assertEquals("NICU", nicu.get("destination"));

        Map<String, Object> defaulted = service.recordNeonatalHandover(episodeId, Map.of(
                "babyCpid", "CPID-PROVISIONAL-BABY"));
        assertEquals("POSTNATAL", defaulted.get("destination"), "well baby stays with mother by default");
    }

    @Test
    void neonatalDestinationNormalisation() {
        assertEquals("NEONATAL", TheatreService.normaliseNeonatalDestination("neonatal"));
        assertEquals("SCBU", TheatreService.normaliseNeonatalDestination("SCBU"));
        assertEquals("POSTNATAL", TheatreService.normaliseNeonatalDestination(null));
        assertEquals("POSTNATAL", TheatreService.normaliseNeonatalDestination("made-up"));
    }
}
