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
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DischargeClearanceEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DischargeSummaryEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.DischargeClearanceRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.DischargeSummaryRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DischargeSummaryServiceTest {

    @Mock DischargeSummaryRepository summaryRepository;
    @Mock DischargeClearanceRepository clearanceRepository;
    @Mock EventOutboxRepository outboxRepository;

    private DischargeSummaryService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID encounter = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DischargeSummaryService(summaryRepository, clearanceRepository,
                outboxRepository, new ObjectMapper(), false);
        setActor("actor-doc");
    }

    private void setActor(String actorId) {
        TrustContextHolder.set(new TrustContext(tenant, actorId, "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    private DischargeSummaryEntity draft() {
        DischargeSummaryEntity s = new DischargeSummaryEntity();
        s.setSummaryId(UUID.randomUUID());
        s.setTenantId(tenant);
        s.setEncounterId(encounter);
        s.setSubjectCpid("CPID-1");
        s.setStatus("DRAFT");
        s.setHospitalCourse("Admitted with pneumonia, IV antibiotics, improved.");
        return s;
    }

    private DischargeClearanceEntity clearance(String type, String status) {
        DischargeClearanceEntity c = new DischargeClearanceEntity();
        c.setClearanceType(type);
        c.setStatus(status);
        return c;
    }

    @Test
    void finalise_blockedWhenClearancePending() {
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(draft()));
        when(clearanceRepository.findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(tenant, encounter))
                .thenReturn(List.of(clearance("CLINICAL", "CLEARED"), clearance("PHARMACY", "PENDING")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.finalise(encounter));
        assertTrue(ex.getMessage().contains("PHARMACY"));
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void finalise_blockedWhenNoClearancesInitialised() {
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(draft()));
        when(clearanceRepository.findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(tenant, encounter))
                .thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.finalise(encounter));
        assertTrue(ex.getMessage().contains("not initialised"));
    }

    @Test
    void finalise_succeeds_emitsFhirCompositionAndFollowup_whenAllCleared() {
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(draft()));
        when(clearanceRepository.findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(tenant, encounter))
                .thenReturn(List.of(clearance("CLINICAL", "CLEARED"), clearance("PHARMACY", "WAIVED")));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.finalise(encounter);
        assertEquals("FINALISED", result.get("status"));
        assertTrue(String.valueOf(result.get("fhir_composition")).contains("\"resourceType\":\"Composition\""));
        assertTrue(String.valueOf(result.get("fhir_composition")).contains("18842-5")); // LOINC discharge summary

        ArgumentCaptor<EventOutboxEntity> evt = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(evt.capture());
        List<String> types = evt.getAllValues().stream().map(EventOutboxEntity::getEventType).toList();
        assertTrue(types.contains("inpatient.discharge.summary_finalised"));
        assertTrue(types.contains("inpatient.discharge.followup_requested"));
    }

    // ── Countersign gate (P3 PCT care tracker) ──────────────────────────────

    private DischargeSummaryEntity countersignDraft() {
        DischargeSummaryEntity s = draft();
        s.setCountersignRequired(true);
        s.setAuthoredBy("actor-doc");
        return s;
    }

    private List<DischargeClearanceEntity> allCleared() {
        return List.of(clearance("CLINICAL", "CLEARED"), clearance("PHARMACY", "WAIVED"));
    }

    @Test
    void finalise_blockedWhenCountersignRequiredAndMissing() {
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(countersignDraft()));
        when(clearanceRepository.findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(tenant, encounter))
                .thenReturn(allCleared());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.finalise(encounter));
        assertTrue(ex.getMessage().contains("countersignature"));
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void countersign_byDifferentActor_unblocksFinalise_andEmitsEvent() {
        DischargeSummaryEntity s = countersignDraft();
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(s));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        setActor("actor-supervisor");
        Map<String, Object> countersigned = service.countersign(encounter, "Reviewed and agreed");
        assertEquals("actor-supervisor", countersigned.get("countersigned_by"));

        ArgumentCaptor<EventOutboxEntity> evt = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(evt.capture());
        assertTrue(evt.getAllValues().stream().map(EventOutboxEntity::getEventType)
                .anyMatch("inpatient.discharge.summary_countersigned"::equals));

        when(clearanceRepository.findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(tenant, encounter))
                .thenReturn(allCleared());
        Map<String, Object> result = service.finalise(encounter);
        assertEquals("FINALISED", result.get("status"));
    }

    @Test
    void countersign_rejectedForAuthor() {
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(countersignDraft()));

        // trust context actor is actor-doc, which is also authoredBy
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.countersign(encounter, null));
        assertTrue(ex.getMessage().contains("own discharge summary"));
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void countersign_rejectedWhenNotRequired() {
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(draft()));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.countersign(encounter, null));
        assertTrue(ex.getMessage().contains("does not require"));
    }

    @Test
    void countersign_rejectedWhenAlreadyCountersigned() {
        DischargeSummaryEntity s = countersignDraft();
        s.setCountersignedBy("actor-supervisor");
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(s));
        setActor("actor-other-supervisor");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.countersign(encounter, null));
        assertTrue(ex.getMessage().contains("already countersigned"));
    }

    @Test
    void countersign_rejectedWhenAlreadyFinalised() {
        DischargeSummaryEntity s = countersignDraft();
        s.setStatus("FINALISED");
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(s));
        setActor("actor-supervisor");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.countersign(encounter, null));
        assertTrue(ex.getMessage().contains("already finalised"));
    }

    @Test
    void saveDraft_afterCountersign_invalidatesCountersignature() {
        DischargeSummaryEntity s = countersignDraft();
        s.setCountersignedBy("actor-supervisor");
        s.setCountersignedAt(java.time.OffsetDateTime.now());
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(s));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.saveDraft(Map.of(
                "encounterId", encounter.toString(),
                "patientId", "CPID-1",
                "hospitalCourse", "Edited after countersign"));

        assertEquals(Boolean.TRUE, result.get("countersign_required"));
        assertEquals(null, result.get("countersigned_by"));

        ArgumentCaptor<EventOutboxEntity> evt = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(evt.capture());
        assertTrue(evt.getAllValues().stream().map(EventOutboxEntity::getEventType)
                .anyMatch("inpatient.discharge.summary_countersign_invalidated"::equals));
    }

    @Test
    void saveDraft_countersignRequirementIsStickyAndDeclarable() {
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> created = service.saveDraft(Map.of(
                "encounterId", encounter.toString(),
                "patientId", "CPID-1",
                "countersign_required", "true"));
        assertEquals(Boolean.TRUE, created.get("countersign_required"));
        assertEquals("actor-doc", created.get("authored_by"));

        // Re-save WITHOUT the flag: requirement must not silently drop.
        DischargeSummaryEntity persisted = countersignDraft();
        persisted.setSummaryId(UUID.randomUUID());
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(persisted));
        Map<String, Object> resaved = service.saveDraft(Map.of(
                "encounterId", encounter.toString(),
                "patientId", "CPID-1"));
        assertEquals(Boolean.TRUE, resaved.get("countersign_required"));
    }

    @Test
    void saveDraft_usesConfiguredCountersignDefault() {
        DischargeSummaryService defaultOn = new DischargeSummaryService(summaryRepository,
                clearanceRepository, outboxRepository, new ObjectMapper(), true);
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> created = defaultOn.saveDraft(Map.of(
                "encounterId", encounter.toString(),
                "patientId", "CPID-1"));
        assertEquals(Boolean.TRUE, created.get("countersign_required"));
    }
}
