package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import zw.gov.mohcc.impilo.inpatient.integration.InventoryConsumptionClient;
import zw.gov.mohcc.impilo.inpatient.integration.OrosOrderClient;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EmergencyActivationEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ResuscitationEventEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ResuscitationRecordEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

/**
 * Resus concurrency hardening (V201, W6b). The database's own guarantees (client_event_id partial
 * unique index, record_version @Version column) are proven on real Postgres separately; this proves
 * the application layer: cpr_cycles/defibrillations/rosc_achieved are a pure DERIVATION of the event
 * log — never client-set — and a stale expected_version is refused rather than silently overwritten.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResuscitationConcurrencyHardeningTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID ACTIVATION = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Mock CarePlanRepository carePlanRepository;
    @Mock CarePlanGoalRepository goalRepository;
    @Mock CarePlanInterventionRepository interventionRepository;
    @Mock FluidBalanceRepository fluidBalanceRepository;
    @Mock ClinicalChartEntryRepository chartEntryRepository;
    @Mock MarEntryRepository marEntryRepository;
    @Mock EarlyWarningScoreRepository ewsRepository;
    @Mock EmergencyActivationRepository emergencyRepository;
    @Mock EmergencyActionRepository emergencyActionRepository;
    @Mock ResuscitationRecordRepository resuscitationRecordRepository;
    @Mock ApgarScoreRepository apgarScoreRepository;
    @Mock ShiftHandoverRepository handoverRepository;
    @Mock ShiftHandoverItemRepository handoverItemRepository;
    @Mock WardPatientAlertRepository wardAlertRepository;
    @Mock DischargeClearanceRepository dischargeClearanceRepository;
    @Mock ResuscitationPhaseRepository resuscitationPhaseRepository;
    @Mock AdmissionRepository admissionRepository;
    @Mock TransferRepository transferRepository;
    @Mock InpatientSafetyService safetyService;
    @Mock InventoryConsumptionClient inventoryConsumptionClient;
    @Mock OrosOrderClient orosOrderClient;
    @Mock EventOutboxRepository outboxRepository;
    @Mock ResuscitationEventRepository resuscitationEventRepository;

    private InpatientClinicalService service;
    private final List<ResuscitationEventEntity> eventStore = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new InpatientClinicalService(
                carePlanRepository, goalRepository, interventionRepository, fluidBalanceRepository,
                chartEntryRepository, marEntryRepository, ewsRepository, emergencyRepository,
                emergencyActionRepository, resuscitationRecordRepository, apgarScoreRepository,
                handoverRepository, handoverItemRepository, wardAlertRepository,
                dischargeClearanceRepository, resuscitationPhaseRepository, admissionRepository,
                transferRepository, new ObjectMapper(), safetyService, inventoryConsumptionClient,
                orosOrderClient, outboxRepository);
        ReflectionTestUtils.setField(service, "resuscitationEventRepository", resuscitationEventRepository);

        TrustContextHolder.set(new TrustContext(TENANT, "nurse-A", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));

        EmergencyActivationEntity activation = new EmergencyActivationEntity();
        activation.setActivationId(ACTIVATION);
        activation.setTenantId(TENANT);
        when(emergencyRepository.findById(ACTIVATION)).thenReturn(Optional.of(activation));

        eventStore.clear();
        when(resuscitationEventRepository.findByActivationIdOrderByRecordedAtAsc(ACTIVATION))
                .thenAnswer(inv -> new ArrayList<>(eventStore));
        when(resuscitationEventRepository.save(any())).thenAnswer(inv -> {
            ResuscitationEventEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            if (e.getRecordedAt() == null) e.setRecordedAt(OffsetDateTime.now());
            eventStore.add(e);
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    /** Simulates JPA: no PrePersist fires under a plain mock save, so the "DB" must assign an id. */
    private ResuscitationRecordEntity[] recordStore() {
        ResuscitationRecordEntity[] holder = new ResuscitationRecordEntity[1];
        when(resuscitationRecordRepository.findFirstByActivationIdOrderByCreatedAtDesc(ACTIVATION))
                .thenAnswer(inv -> Optional.ofNullable(holder[0]));
        when(resuscitationRecordRepository.save(any())).thenAnswer(inv -> {
            ResuscitationRecordEntity r = inv.getArgument(0);
            if (r.getResusId() == null) r.setResusId(UUID.randomUUID());
            if (r.getRecordVersion() == null) r.setRecordVersion(0L);
            else r.setRecordVersion(r.getRecordVersion() + 1);
            holder[0] = r;
            return r;
        });
        return holder;
    }

    private Map<String, Object> event(String channel, String code, String valueText) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("channel", channel);
        b.put("code", code);
        if (valueText != null) b.put("valueText", valueText);
        return b;
    }

    @Test
    @DisplayName("cpr_cycles and defibrillations are derived by counting CPR events, never client-settable")
    void cprCyclesDerivedFromEvents() {
        recordStore();

        service.recordResuscitationEvent(ACTIVATION, event("CPR", "CYCLE", null));
        service.recordResuscitationEvent(ACTIVATION, event("CPR", "CYCLE", null));
        service.recordResuscitationEvent(ACTIVATION, event("CPR", "DEFIBRILLATION", null));
        service.recordResuscitationEvent(ACTIVATION, event("CPR", "CYCLE", null));
        Map<String, Object> summary = service.getResuscitation(ACTIVATION);

        assertThat(summary.get("cpr_cycles")).isEqualTo(3);
        assertThat(summary.get("defibrillations")).isEqualTo(1);
    }

    @Test
    @DisplayName("attempting to set cpr_cycles directly via recordResuscitation has no effect — it stays derived")
    void directCprCyclesOverrideIgnored() {
        recordStore();
        service.recordResuscitationEvent(ACTIVATION, event("CPR", "CYCLE", null)); // one real cycle

        Map<String, Object> attempt = new LinkedHashMap<>();
        attempt.put("cprCycles", 99); // no longer read by the service at all
        Map<String, Object> result = service.recordResuscitation(ACTIVATION, attempt);

        assertThat(result.get("cpr_cycles")).isEqualTo(1);
    }

    @Test
    @DisplayName("rosc_achieved and rosc_time derive from the first RHYTHM/ROSC event, timed at ITS recorded_at")
    void roscDerivedFromRhythmEvents() {
        recordStore();
        service.recordResuscitationEvent(ACTIVATION, event("RHYTHM", null, "VF"));
        Map<String, Object> afterRosc = service.recordResuscitationEvent(ACTIVATION, event("RHYTHM", "ROSC", "Sinus"));

        assertThat(afterRosc.get("rosc_achieved")).isEqualTo(true);
        assertThat(afterRosc.get("initial_rhythm")).isEqualTo("VF");
        assertThat(afterRosc.get("current_rhythm")).isEqualTo("Sinus");
    }

    @Test
    @DisplayName("final_rhythm is stamped only when an outcome is first supplied — the stand-down moment")
    void finalRhythmStampedOnlyAtStandDown() {
        recordStore();
        service.recordResuscitationEvent(ACTIVATION, event("RHYTHM", null, "VF"));
        service.recordResuscitationEvent(ACTIVATION, event("RHYTHM", "ROSC", "Sinus"));

        Map<String, Object> beforeStandDown = service.getResuscitation(ACTIVATION);
        assertThat(beforeStandDown.get("final_rhythm")).isNull();

        Map<String, Object> standDown = new LinkedHashMap<>();
        standDown.put("outcome", "ROSC_SUSTAINED");
        standDown.put("standDownReason", "sustained circulation for 20 minutes");
        Map<String, Object> after = service.recordResuscitation(ACTIVATION, standDown);

        assertThat(after.get("final_rhythm")).isEqualTo("Sinus");
        assertThat(after.get("outcome")).isEqualTo("ROSC_SUSTAINED");
    }

    @Test
    @DisplayName("a duplicate client_event_id (single-event endpoint) is refused with a clear 409, not an opaque 500")
    void duplicateClientEventIdRefused() {
        recordStore();
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_resuscitation_event_client_id\""))
                .when(resuscitationEventRepository).save(any());

        Map<String, Object> b = event("CPR", "CYCLE", null);
        b.put("clientEventId", "device-123-evt-1");
        assertThatThrownBy(() -> service.recordResuscitationEvent(ACTIVATION, b))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already recorded");
    }

    @Test
    @DisplayName("a batch retried whole (all ids already recorded from a prior attempt) skips every entry via the pre-check")
    void batchSkipsAlreadyRecordedFromPriorAttempt() {
        recordStore();
        when(resuscitationEventRepository.existsByActivationIdAndClientEventId(ACTIVATION, "prior-1")).thenReturn(true);
        when(resuscitationEventRepository.existsByActivationIdAndClientEventId(ACTIVATION, "prior-2")).thenReturn(false);

        var alreadyRecorded = event("CPR", "CYCLE", null);
        alreadyRecorded.put("clientEventId", "prior-1");
        var genuinelyNew = event("CPR", "CYCLE", null);
        genuinelyNew.put("clientEventId", "prior-2");

        List<Map<String, Object>> results = service.recordResuscitationEventsBatch(
                ACTIVATION, List.of(alreadyRecorded, genuinelyNew));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("status")).isEqualTo("DUPLICATE_SKIPPED");
        assertThat(results.get(1).get("status")).isEqualTo("RECORDED");
        assertThat(eventStore).hasSize(1); // only the genuinely new one was actually inserted
    }

    @Test
    @DisplayName("a malformed batch repeating the SAME client_event_id twice in one call skips the second, never touching the DB constraint")
    void batchSkipsWithinBatchDuplicate() {
        recordStore();
        when(resuscitationEventRepository.existsByActivationIdAndClientEventId(ACTIVATION, "dup-in-batch")).thenReturn(false);

        var first = event("CPR", "CYCLE", null);
        first.put("clientEventId", "dup-in-batch");
        var repeated = event("CPR", "CYCLE", null);
        repeated.put("clientEventId", "dup-in-batch");

        List<Map<String, Object>> results = service.recordResuscitationEventsBatch(
                ACTIVATION, List.of(first, repeated));

        assertThat(results.get(0).get("status")).isEqualTo("RECORDED");
        assertThat(results.get(1).get("status")).isEqualTo("DUPLICATE_SKIPPED");
        assertThat(eventStore).hasSize(1);
    }

    @Test
    @DisplayName("a stale expected_version is refused as a conflict, not silently overwritten")
    void staleExpectedVersionRefused() {
        var holder = recordStore();
        // Establish a record at version 0, then simulate a concurrent edit bumping it to 1.
        service.recordResuscitation(ACTIVATION, Map.of("teamLeader", "Dr A"));
        holder[0].setRecordVersion(holder[0].getRecordVersion() + 1); // a concurrent writer already saved

        Map<String, Object> staleEdit = new LinkedHashMap<>();
        staleEdit.put("teamLeader", "Dr B");
        staleEdit.put("expectedVersion", "0"); // the caller last read version 0, which is now stale

        assertThatThrownBy(() -> service.recordResuscitation(ACTIVATION, staleEdit))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("a matching expected_version succeeds")
    void matchingExpectedVersionSucceeds() {
        var holder = recordStore();
        service.recordResuscitation(ACTIVATION, Map.of("teamLeader", "Dr A"));
        long currentVersion = holder[0].getRecordVersion();

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("teamLeader", "Dr B");
        edit.put("expectedVersion", String.valueOf(currentVersion));

        Map<String, Object> result = service.recordResuscitation(ACTIVATION, edit);
        assertThat(result.get("team_leader")).isEqualTo("Dr B");
    }

    @Test
    @DisplayName("recording against an unknown activation is refused")
    void unknownActivationRefused() {
        when(emergencyRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordResuscitation(UUID.randomUUID(), Map.of()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
