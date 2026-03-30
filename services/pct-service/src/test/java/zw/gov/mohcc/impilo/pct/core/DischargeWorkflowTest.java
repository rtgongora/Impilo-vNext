package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.DischargeCaseEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.DischargeCaseRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DischargeWorkflow}.
 *
 * <p>Validates the discharge workflow including case creation with correct
 * blockers, blocker clearing mechanics, payment gate enforcement, and
 * discharge completion.</p>
 */
@ExtendWith(MockitoExtension.class)
class DischargeWorkflowTest {

    @Mock
    private DischargeCaseRepository dischargeCaseRepository;

    @Mock
    private JourneyRepository journeyRepository;

    @Mock
    private JourneyStateMachine journeyStateMachine;

    @Mock
    private EventOutboxRepository outboxRepository;

    private DischargeWorkflow dischargeWorkflow;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "actor-123";
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        dischargeWorkflow = new DischargeWorkflow(
                dischargeCaseRepository, journeyRepository,
                journeyStateMachine, outboxRepository, objectMapper);
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, null, null, "national", AccessMode.INTERNAL);
    }

    private JourneyEntity createJourney(String journeyId, JourneyState state) {
        JourneyEntity journey = new JourneyEntity();
        journey.setJourneyId(journeyId);
        journey.setTenantId(TENANT_ID);
        journey.setFacilityId(FACILITY_ID);
        journey.setPatientCpid("CPID-001");
        journey.setState(state);
        return journey;
    }

    @Nested
    @DisplayName("Start Discharge")
    class StartDischargeTests {

        @Test
        @DisplayName("startDischarge creates case with default blockers for STANDARD type")
        void startDischargeWithDefaultBlockers() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                String journeyId = "J-TEST-001";
                JourneyEntity journey = createJourney(journeyId, JourneyState.IN_SERVICE);

                when(journeyRepository.findByJourneyId(journeyId)).thenReturn(Optional.of(journey));
                when(dischargeCaseRepository.save(any(DischargeCaseEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));
                when(journeyStateMachine.transition(any(), any())).thenReturn(journey);

                DischargeCaseEntity result = dischargeWorkflow.startDischarge(journeyId, "STANDARD");

                assertThat(result).isNotNull();
                assertThat(result.getJourneyId()).isEqualTo(journeyId);
                assertThat(result.getDischargeType()).isEqualTo("STANDARD");
                assertThat(result.getStatus()).isEqualTo("INITIATED");

                // Default blockers should include all 5
                String blockers = result.getBlockers();
                assertThat(blockers).contains("CLINICAL");
                assertThat(blockers).contains("BILLING");
                assertThat(blockers).contains("PHARMACY");
                assertThat(blockers).contains("PAYMENT");
                assertThat(blockers).contains("SUMMARY");

                assertThat(result.isClinicalCleared()).isFalse();
                assertThat(result.isBillingCleared()).isFalse();
                assertThat(result.isPharmacyCleared()).isFalse();
                assertThat(result.isPaymentCleared()).isFalse();
                assertThat(result.isSummaryCleared()).isFalse();

                verify(journeyStateMachine).transition(journey, JourneyState.DISCHARGE_PENDING);
            }
        }

        @Test
        @DisplayName("startDischarge for DEATH type excludes PHARMACY and PAYMENT blockers")
        void startDischargeDeathTypeExcludesBlockers() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                String journeyId = "J-TEST-002";
                JourneyEntity journey = createJourney(journeyId, JourneyState.ADMITTED);

                when(journeyRepository.findByJourneyId(journeyId)).thenReturn(Optional.of(journey));
                when(dischargeCaseRepository.save(any(DischargeCaseEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));
                when(journeyStateMachine.transition(any(), any())).thenReturn(journey);

                DischargeCaseEntity result = dischargeWorkflow.startDischarge(journeyId, "DEATH");

                String blockers = result.getBlockers();
                assertThat(blockers).contains("CLINICAL");
                assertThat(blockers).contains("BILLING");
                assertThat(blockers).contains("SUMMARY");
                assertThat(blockers).doesNotContain("PHARMACY");
                assertThat(blockers).doesNotContain("PAYMENT");

                // PHARMACY and PAYMENT should be auto-cleared for death discharges
                assertThat(result.isPharmacyCleared()).isTrue();
                assertThat(result.isPaymentCleared()).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Clear Blocker")
    class ClearBlockerTests {

        @Test
        @DisplayName("clearBlocker removes blocker from array and sets flag")
        void clearBlockerRemovesAndSetsFlag() {
            UUID caseId = UUID.randomUUID();
            DischargeCaseEntity dischargeCase = new DischargeCaseEntity();
            dischargeCase.setId(caseId);
            dischargeCase.setJourneyId("J-TEST-001");
            dischargeCase.setStatus("INITIATED");
            dischargeCase.setBlockers("[\"CLINICAL\",\"BILLING\",\"PHARMACY\",\"PAYMENT\",\"SUMMARY\"]");
            dischargeCase.setClinicalCleared(false);
            dischargeCase.setBillingCleared(false);
            dischargeCase.setPharmacyCleared(false);
            dischargeCase.setPaymentCleared(false);
            dischargeCase.setSummaryCleared(false);

            when(dischargeCaseRepository.findById(caseId)).thenReturn(Optional.of(dischargeCase));
            when(dischargeCaseRepository.save(any(DischargeCaseEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            DischargeCaseEntity result = dischargeWorkflow.clearBlocker(caseId, "CLINICAL");

            assertThat(result.isClinicalCleared()).isTrue();
            assertThat(result.getBlockers()).doesNotContain("CLINICAL");
            assertThat(result.getBlockers()).contains("BILLING");
            assertThat(result.getStatus()).isEqualTo("INITIATED"); // Not yet EXIT_CLEARED
        }

        @Test
        @DisplayName("clearing all blockers auto-progresses to EXIT_CLEARED")
        void clearingAllBlockersAutoProgresses() {
            UUID caseId = UUID.randomUUID();
            DischargeCaseEntity dischargeCase = new DischargeCaseEntity();
            dischargeCase.setId(caseId);
            dischargeCase.setJourneyId("J-TEST-001");
            dischargeCase.setStatus("INITIATED");
            dischargeCase.setBlockers("[\"SUMMARY\"]");
            dischargeCase.setClinicalCleared(true);
            dischargeCase.setBillingCleared(true);
            dischargeCase.setPharmacyCleared(true);
            dischargeCase.setPaymentCleared(true);
            dischargeCase.setSummaryCleared(false);

            when(dischargeCaseRepository.findById(caseId)).thenReturn(Optional.of(dischargeCase));
            when(dischargeCaseRepository.save(any(DischargeCaseEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            DischargeCaseEntity result = dischargeWorkflow.clearBlocker(caseId, "SUMMARY");

            assertThat(result.isSummaryCleared()).isTrue();
            assertThat(result.getStatus()).isEqualTo("EXIT_CLEARED");
        }

        @Test
        @DisplayName("clearBlocker throws when blocker is not active")
        void clearBlockerThrowsWhenNotActive() {
            UUID caseId = UUID.randomUUID();
            DischargeCaseEntity dischargeCase = new DischargeCaseEntity();
            dischargeCase.setId(caseId);
            dischargeCase.setStatus("INITIATED");
            dischargeCase.setBlockers("[\"BILLING\",\"SUMMARY\"]");
            dischargeCase.setClinicalCleared(true);

            when(dischargeCaseRepository.findById(caseId)).thenReturn(Optional.of(dischargeCase));

            assertThatThrownBy(() -> dischargeWorkflow.clearBlocker(caseId, "CLINICAL"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CLINICAL")
                    .hasMessageContaining("is not active");
        }
    }

    @Nested
    @DisplayName("Complete Discharge")
    class CompleteDischargeTests {

        @Test
        @DisplayName("completeDischarge transitions journey to DISCHARGED")
        void completeDischargeTransitionsJourney() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                UUID caseId = UUID.randomUUID();
                DischargeCaseEntity dischargeCase = new DischargeCaseEntity();
                dischargeCase.setId(caseId);
                dischargeCase.setJourneyId("J-TEST-001");
                dischargeCase.setDischargeType("STANDARD");
                dischargeCase.setStatus("EXIT_CLEARED");
                dischargeCase.setBlockers("[]");

                JourneyEntity journey = createJourney("J-TEST-001", JourneyState.DISCHARGE_PENDING);

                when(dischargeCaseRepository.findById(caseId)).thenReturn(Optional.of(dischargeCase));
                when(dischargeCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(journeyRepository.findByJourneyId("J-TEST-001")).thenReturn(Optional.of(journey));
                when(journeyStateMachine.transition(any(), any())).thenReturn(journey);

                DischargeCaseEntity result = dischargeWorkflow.completeDischarge(caseId);

                assertThat(result.getStatus()).isEqualTo("COMPLETED");
                assertThat(result.getClosedAt()).isNotNull();
                verify(journeyStateMachine).transition(journey, JourneyState.DISCHARGED);
            }
        }

        @Test
        @DisplayName("completeDischarge throws when case is already COMPLETED")
        void completeDischargeThrowsWhenAlreadyCompleted() {
            UUID caseId = UUID.randomUUID();
            DischargeCaseEntity dischargeCase = new DischargeCaseEntity();
            dischargeCase.setId(caseId);
            dischargeCase.setStatus("COMPLETED");

            when(dischargeCaseRepository.findById(caseId)).thenReturn(Optional.of(dischargeCase));

            assertThatThrownBy(() -> dischargeWorkflow.completeDischarge(caseId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot complete discharge");
        }
    }
}
