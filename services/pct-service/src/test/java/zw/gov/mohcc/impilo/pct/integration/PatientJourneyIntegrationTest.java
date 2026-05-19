package zw.gov.mohcc.impilo.pct.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.core.*;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.*;
import zw.gov.mohcc.impilo.pct.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration test for a complete patient journey lifecycle.
 *
 * <p>Simulates the full patient flow through the PCT system using mocked
 * repositories. Verifies that journey state transitions are correct at
 * each step from arrival through discharge.</p>
 *
 * <p>Flow tested:
 * Start shift -&gt; create journey -&gt; triage -&gt; enqueue -&gt; call next -&gt;
 * start encounter -&gt; admit -&gt; start discharge -&gt; clear all blockers -&gt;
 * complete discharge.</p>
 */
@ExtendWith(MockitoExtension.class)
class PatientJourneyIntegrationTest {

    @Mock private JourneyRepository journeyRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private TriageRecordRepository triageRecordRepository;
    @Mock private QueueItemRepository queueItemRepository;
    @Mock private EncounterRepository encounterRepository;
    @Mock private AdmissionRepository admissionRepository;
    @Mock private DischargeCaseRepository dischargeCaseRepository;
    @Mock private WorkspaceSessionRepository sessionRepository;
    @Mock private TelemetryEventRepository telemetryRepository;
    @Mock private TransferRepository transferRepository;

    private WorkspaceSessionService workspaceSessionService;
    private JourneyStateMachine journeyStateMachine;
    private TriageService triageService;
    private QueueEngine queueEngine;
    private RoutingEngine routingEngine;
    private EncounterService encounterService;
    private AdmissionWorkflow admissionWorkflow;
    private TransferService transferService;
    private DischargeWorkflow dischargeWorkflow;
    private TelemetryService telemetryService;

    private ObjectMapper objectMapper;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "dr-smith";
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final UUID QUEUE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        telemetryService = new TelemetryService(telemetryRepository, objectMapper);

        journeyStateMachine = new JourneyStateMachine(
                journeyRepository, outboxRepository, telemetryService, objectMapper);

        workspaceSessionService = new WorkspaceSessionService(
                sessionRepository, outboxRepository, telemetryService, objectMapper);

        triageService = new TriageService(
                triageRecordRepository, journeyRepository, journeyStateMachine,
                outboxRepository, objectMapper);

        queueEngine = new QueueEngine(
                queueItemRepository, journeyRepository, journeyStateMachine,
                outboxRepository, objectMapper);

        routingEngine = new RoutingEngine(
                queueEngine, journeyRepository, triageRecordRepository,
                telemetryService, objectMapper);

        encounterService = new EncounterService(
                encounterRepository, journeyRepository, journeyStateMachine,
                outboxRepository, objectMapper);

        admissionWorkflow = new AdmissionWorkflow(
                admissionRepository, journeyRepository, journeyStateMachine,
                outboxRepository, telemetryService, objectMapper);

        transferService = new TransferService(
                transferRepository, admissionRepository, journeyRepository,
                journeyStateMachine, outboxRepository, telemetryService, objectMapper);

        dischargeWorkflow = new DischargeWorkflow(
                dischargeCaseRepository, journeyRepository, journeyStateMachine,
                admissionWorkflow, transferService, outboxRepository, objectMapper);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID, null, AccessMode.INTERNAL);
    }

    @Test
    @DisplayName("Complete patient journey: arrival through discharge")
    void completePatientJourney() {
        try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
            mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

            // Configure repository mocks to echo saves and track state changes
            when(journeyRepository.save(any(JourneyEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(outboxRepository.save(any(EventOutboxEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(triageRecordRepository.save(any(TriageRecordEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(queueItemRepository.save(any(QueueItemEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(encounterRepository.save(any(EncounterEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(admissionRepository.save(any(AdmissionEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(dischargeCaseRepository.save(any(DischargeCaseEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(sessionRepository.save(any(WorkspaceSessionEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Mock session lookup (no existing session)
            when(sessionRepository.findByTenantIdAndActorIdAndEndedAtIsNull(TENANT_ID, ACTOR_ID))
                    .thenReturn(Optional.empty());

            // ======== Step 1: Start Shift ========
            WorkspaceSessionEntity session = workspaceSessionService.startShift(
                    FACILITY_ID, WORKSPACE_ID, "CLINICAL");
            assertThat(session).isNotNull();
            assertThat(session.getDutyMode()).isEqualTo("CLINICAL");

            // ======== Step 2: Create Journey ========
            JourneyEntity journey = journeyStateMachine.createJourney(
                    FACILITY_ID, "CPID-PATIENT-001", "SELF", null);
            assertThat(journey.getState()).isEqualTo(JourneyState.ARRIVED);
            String journeyId = journey.getJourneyId();

            // Mock journey lookup for subsequent calls
            when(journeyRepository.findByJourneyId(journeyId))
                    .thenReturn(Optional.of(journey));

            // ======== Step 3: Triage ========
            TriageRecordEntity triageRecord = triageService.recordTriage(
                    journeyId, 2, "{\"bp\":\"120/80\",\"temp\":37.2}", "Moderate fever");
            assertThat(triageRecord.getAcuity()).isEqualTo(2);
            assertThat(journey.getState()).isEqualTo(JourneyState.TRIAGED);

            // ======== Step 4: Enqueue ========
            when(queueItemRepository.findMaxTokenNumberByQueueId(QUEUE_ID)).thenReturn(0);

            QueueItemEntity queueItem = queueEngine.enqueue(QUEUE_ID, journeyId, 4);
            assertThat(queueItem.getTokenNumber()).isEqualTo(1);
            assertThat(queueItem.getStatus()).isEqualTo(QueueItemStatus.WAITING);
            assertThat(journey.getState()).isEqualTo(JourneyState.QUEUED);

            // ======== Step 5: Call Next ========
            when(queueItemRepository.findFirstByQueueIdAndStatusOrderByPriorityDescCreatedAtAsc(
                    QUEUE_ID, QueueItemStatus.WAITING))
                    .thenReturn(Optional.of(queueItem));

            QueueItemEntity calledItem = queueEngine.callNext(QUEUE_ID);
            assertThat(calledItem).isNotNull();
            assertThat(calledItem.getStatus()).isEqualTo(QueueItemStatus.CALLED);
            assertThat(calledItem.getCalledBy()).isEqualTo(ACTOR_ID);

            // ======== Step 6: Start Encounter ========
            EncounterEntity encounter = encounterService.startEncounter(
                    journeyId,
                    "CONSULTATION",
                    "outpatient",
                    "walk_in",
                    "in_person",
                    null,
                    "facility",
                    "routine",
                    null,
                    null,
                    null);
            assertThat(encounter.getEncounterType()).isEqualTo("CONSULTATION");
            assertThat(encounter.getStatus()).isEqualTo("STARTED");
            assertThat(journey.getState()).isEqualTo(JourneyState.IN_SERVICE);

            // ======== Step 7: Admit Patient ========
            UUID wardId = UUID.randomUUID();
            UUID bedId = UUID.randomUUID();
            AdmissionEntity admission = admissionWorkflow.requestAdmission(journeyId, wardId, bedId);
            assertThat(admission.getStatus()).isEqualTo("REQUESTED");

            // Mock admission lookup for admit step
            when(admissionRepository.findById(admission.getId()))
                    .thenReturn(Optional.of(admission));

            // Approve and admit
            admission.setStatus("APPROVED");
            AdmissionEntity admittedAdmission = admissionWorkflow.admitPatient(admission.getId());
            assertThat(admittedAdmission.getStatus()).isEqualTo("ADMITTED");
            assertThat(journey.getState()).isEqualTo(JourneyState.ADMITTED);

            // ======== Step 8: Start Discharge ========
            DischargeCaseEntity dischargeCase = dischargeWorkflow.startDischarge(journeyId, "STANDARD");
            assertThat(dischargeCase.getStatus()).isEqualTo("INITIATED");
            assertThat(journey.getState()).isEqualTo(JourneyState.DISCHARGE_PENDING);

            // Mock discharge case lookup
            when(dischargeCaseRepository.findById(dischargeCase.getId()))
                    .thenReturn(Optional.of(dischargeCase));

            // ======== Step 9: Clear All Blockers ========
            dischargeWorkflow.clearBlocker(dischargeCase.getId(), "CLINICAL");
            assertThat(dischargeCase.isClinicalCleared()).isTrue();

            dischargeWorkflow.clearBlocker(dischargeCase.getId(), "BILLING");
            assertThat(dischargeCase.isBillingCleared()).isTrue();

            dischargeWorkflow.clearBlocker(dischargeCase.getId(), "PHARMACY");
            assertThat(dischargeCase.isPharmacyCleared()).isTrue();

            dischargeWorkflow.clearBlocker(dischargeCase.getId(), "PAYMENT");
            assertThat(dischargeCase.isPaymentCleared()).isTrue();

            dischargeWorkflow.clearBlocker(dischargeCase.getId(), "SUMMARY");
            assertThat(dischargeCase.isSummaryCleared()).isTrue();

            // Case should auto-progress to EXIT_CLEARED
            assertThat(dischargeCase.getStatus()).isEqualTo("EXIT_CLEARED");

            // ======== Step 10: Complete Discharge ========
            DischargeCaseEntity completedCase = dischargeWorkflow.completeDischarge(dischargeCase.getId());
            assertThat(completedCase.getStatus()).isEqualTo("COMPLETED");
            assertThat(completedCase.getClosedAt()).isNotNull();
            assertThat(journey.getState()).isEqualTo(JourneyState.DISCHARGED);

            // ======== Verify Final State ========
            assertThat(journey.getState()).isEqualTo(JourneyState.DISCHARGED);

            // Verify outbox events were written for each major step
            // At minimum: journey created, state changes, triage, enqueue, encounter, admission, discharge
            verify(outboxRepository, atLeast(5)).save(any(EventOutboxEntity.class));
        }
    }
}
