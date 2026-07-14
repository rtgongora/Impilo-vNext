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
import zw.gov.mohcc.impilo.inpatient.integration.TheatreReadinessClient.ReadinessResult;
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

/**
 * Wave 1 clinical-safety: the readiness BLOOD gate now requires REAL MADI reservation+crossmatch, and a
 * surgical-count discrepancy raises a RITO RETAINED_ITEM sentinel + routes a safety event. All peers are
 * mocked — these tests assert the orchestration-by-reference behaviour, not the peer internals.
 */
@ExtendWith(MockitoExtension.class)
class TheatreClinicalSafetyTest {

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

    private TheatreService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TheatreService(episodeRepository, readinessRepository, noteRepository, safetyRepository,
                checklistRepository, outboxRepository, episodeService, orosOrderClient, readinessClient,
                deathClient, butanoClient, bloodLinkRepository, transportRepository, specimenRepository,
                countRepository, madiBloodClient, nhumeTransportClient, orosSpecimenClient, ritoSafetyClient,
                new ObjectMapper());
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(bloodLinkRepository.save(any())).thenAnswer(i -> {
            ProcedureBloodLinkEntity l = i.getArgument(0);
            if (l.getLinkId() == null) l.setLinkId(UUID.randomUUID());
            return l;
        });
        lenient().when(countRepository.save(any())).thenAnswer(i -> {
            ProcedureCountEntity c = i.getArgument(0);
            if (c.getCountId() == null) c.setCountId(UUID.randomUUID());
            return c;
        });
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    private ProcedureEpisodeEntity episode() {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setEpisodeId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-THEATRE-1");
        e.setSurgeonId("PROV-SURG-1");
        e.setTheatreRoomId(UUID.randomUUID());
        e.setStatus("READY_FOR_THEATRE");
        return e;
    }

    private void stubReadinessLegsReady() {
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(episode()));
        when(readinessClient.checkProviderScope(any(), eq("SURGERY")))
                .thenReturn(ReadinessResult.ready("varapi:PROV-SURG-1", Map.of()));
        when(readinessClient.checkTeamAvailability(any()))
                .thenReturn(ReadinessResult.ready("vashandi", Map.of()));
        when(readinessClient.checkEquipment(any()))
                .thenReturn(ReadinessResult.ready("asset-registry", Map.of()));
        when(episodeService.getEpisode(episodeId)).thenReturn(Map.of("preop_assessments",
                List.of(Map.of("assessment_type", "ANAESTHESIA", "cleared_for_theatre", true))));
        when(readinessRepository.save(any())).thenAnswer(i -> {
            ProcedureReadinessCheckEntity r = i.getArgument(0);
            r.setReadinessId(UUID.randomUUID());
            return r;
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bloodCheck(Map<String, Object> readiness) {
        return ((List<Map<String, Object>>) readiness.get("checks")).stream()
                .filter(c -> "BLOOD".equals(c.get("domain"))).findFirst().orElseThrow();
    }

    @Test
    void bloodGate_blockedWhenMadiNotReserved() {
        stubReadinessLegsReady();
        ProcedureBloodLinkEntity link = new ProcedureBloodLinkEntity();
        link.setEpisodeId(episodeId);
        link.setMadiOrderId("MADI-ORDER-1");
        link.setRequired(true);
        when(bloodLinkRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId)).thenReturn(Optional.of(link));
        // MADI still crossmatch-pending → not reserved.
        when(madiBloodClient.getOrderDetail("MADI-ORDER-1"))
                .thenReturn(new MadiBloodClient.BloodOrderView("CROSSMATCH_PENDING", "PENDING", "PENDING", true));

        Map<String, Object> readiness = service.evaluateReadiness(episodeId, Map.of("bloodRequired", true));

        assertFalse((Boolean) readiness.get("bookable"), "unreserved blood must block booking");
        assertEquals("BLOCKED", bloodCheck(readiness).get("status"));
    }

    @Test
    void bloodGate_readyOnlyWithRealMadiReservationAndCompatibleCrossmatch() {
        stubReadinessLegsReady();
        ProcedureBloodLinkEntity link = new ProcedureBloodLinkEntity();
        link.setEpisodeId(episodeId);
        link.setMadiOrderId("MADI-ORDER-1");
        link.setRequired(true);
        when(bloodLinkRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId)).thenReturn(Optional.of(link));
        when(madiBloodClient.getOrderDetail("MADI-ORDER-1"))
                .thenReturn(new MadiBloodClient.BloodOrderView("RESERVED", "RESERVED", "COMPATIBLE", true));

        Map<String, Object> readiness = service.evaluateReadiness(episodeId, Map.of("bloodRequired", true));

        assertEquals("READY", bloodCheck(readiness).get("status"));
        assertTrue((Boolean) readiness.get("bookable"), "real MADI reservation should clear the blood gate");
    }

    @Test
    void bloodGate_notRequiredSkipsGateAndStaysBookable() {
        stubReadinessLegsReady();
        when(bloodLinkRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId)).thenReturn(Optional.empty());

        Map<String, Object> readiness = service.evaluateReadiness(episodeId, Map.of("bloodRequired", false));

        assertEquals("NOT_REQUIRED", bloodCheck(readiness).get("status"));
        assertTrue((Boolean) readiness.get("bookable"));
        verify(madiBloodClient, never()).getOrderDetail(any());
    }

    @Test
    void recordCount_discrepancyRaisesRitoRetainedItemSentinel() {
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(episode()));
        when(countRepository.countByEpisodeIdAndKind(episodeId, "SWAB")).thenReturn(0);
        when(countRepository.findByTenantIdAndEpisodeIdAndKindAndSeq(tenant, episodeId, "SWAB", 1))
                .thenReturn(Optional.empty());
        when(ritoSafetyClient.createCase(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("RITO-CASE-9");
        when(safetyRepository.save(any())).thenAnswer(i -> {
            ProcedureSafetyEventEntity ev = i.getArgument(0);
            ev.setSafetyEventId(UUID.randomUUID());
            return ev;
        });

        Map<String, Object> row = service.recordCount(episodeId,
                Map.of("kind", "SWAB", "baselineCount", 10, "closingCount", 9));

        assertEquals(1, row.get("discrepancy"));
        assertEquals(false, row.get("reconciled"));
        assertEquals("RITO-CASE-9", row.get("rito_case_ref"));
        verify(ritoSafetyClient).recordSafetyIncident(eq("RITO-CASE-9"), eq("RETAINED_ITEM"), any(), any(), eq(true), any());
        verify(ritoSafetyClient).ingestQualitySignal(eq("CHECKLIST_NONCOMPLIANCE"), any(), any(), any(), any(), any(), any());
        verify(safetyRepository).save(any()); // routed through procedure_safety_event ownerFor path
    }

    @Test
    void recordCount_reconciledWhenBaselineEqualsClosing() {
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(episode()));
        when(countRepository.countByEpisodeIdAndKind(episodeId, "INSTRUMENT")).thenReturn(0);
        when(countRepository.findByTenantIdAndEpisodeIdAndKindAndSeq(tenant, episodeId, "INSTRUMENT", 1))
                .thenReturn(Optional.empty());

        Map<String, Object> row = service.recordCount(episodeId,
                Map.of("kind", "INSTRUMENT", "baselineCount", 5, "closingCount", 5));

        assertEquals(0, row.get("discrepancy"));
        assertEquals(true, row.get("reconciled"));
        verify(ritoSafetyClient, never()).createCase(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reconcileTransport_firesOverdueSafetyOnceAndIsIdempotent() {
        when(transportRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ProcedureTransportEntity t = new ProcedureTransportEntity();
        t.setTransportId(UUID.randomUUID());
        t.setTenantId(tenant);
        t.setEpisodeId(episodeId);
        t.setKind("PATIENT_MOVEMENT");
        t.setLeg("WARD_TO_THEATRE");
        t.setStatus("IN_TRANSIT");
        t.setNhumeDeliveryId("NH-1");
        t.setSlaDueAt(OffsetDateTime.now().minusMinutes(5)); // already breached
        when(nhumeTransportClient.getDelivery("NH-1"))
                .thenReturn(new NhumeTransportClient.DeliveryView("NH-1", "IN_TRANSIT", t.getSlaDueAt(), true));

        service.reconcileTransport(t);
        assertTrue(t.isOverdue());
        assertEquals("OVERDUE", t.getStatus());
        verify(outboxRepository, times(1)).save(argThat(o ->
                "theatre.transport.overdue".equals(((EventOutboxEntity) o).getEventType())));

        // Second pass must NOT re-emit the overdue SAFETY event (idempotent by the overdue flag).
        service.reconcileTransport(t);
        verify(outboxRepository, times(1)).save(argThat(o ->
                "theatre.transport.overdue".equals(((EventOutboxEntity) o).getEventType())));
    }

    @Test
    void requestBlood_placesMadiOrderAndProjectsGroupScreen() {
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(episode()));
        when(bloodLinkRepository.findFirstByEpisodeIdOrderByCreatedAtDesc(episodeId)).thenReturn(Optional.empty());
        when(madiBloodClient.requestBlood(any(), any(), any(), any(), any(), any(), any())).thenReturn("MADI-ORDER-42");

        Map<String, Object> row = service.requestBlood(episodeId, Map.of("units", 2, "bloodGroup", "O+", "componentType", "PRBC"));

        assertEquals("MADI-ORDER-42", row.get("madi_order_id"));
        assertEquals("GROUP_SCREEN", row.get("status"));
    }
}
