package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
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
 * Unit tests for {@link JourneyStateMachine}.
 *
 * <p>Validates the state transition graph including valid transitions,
 * invalid transitions that must be rejected, and journey creation.</p>
 */
@ExtendWith(MockitoExtension.class)
class JourneyStateMachineTest {

    @Mock
    private JourneyRepository journeyRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    @Mock
    private TelemetryService telemetryService;

    private JourneyStateMachine stateMachine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "actor-123";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        stateMachine = new JourneyStateMachine(
                journeyRepository, outboxRepository, telemetryService, objectMapper);
    }

    private JourneyEntity createJourneyInState(JourneyState state) {
        JourneyEntity journey = new JourneyEntity();
        journey.setJourneyId("J-TEST-001");
        journey.setTenantId(TENANT_ID);
        journey.setFacilityId(FACILITY_ID);
        journey.setPatientCpid("CPID-001");
        journey.setState(state);
        return journey;
    }

    private TrustContext createTrustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, null, null, "national", AccessMode.INTERNAL);
    }

    @Nested
    @DisplayName("Valid State Transitions")
    class ValidTransitions {

        @Test
        @DisplayName("ARRIVED -> TRIAGED is valid")
        void arrivedToTriaged() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                JourneyEntity journey = createJourneyInState(JourneyState.ARRIVED);
                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity result = stateMachine.transition(journey, JourneyState.TRIAGED);

                assertThat(result.getState()).isEqualTo(JourneyState.TRIAGED);
                verify(journeyRepository).save(any(JourneyEntity.class));
                verify(outboxRepository).save(any(EventOutboxEntity.class));
            }
        }

        @Test
        @DisplayName("TRIAGED -> QUEUED is valid")
        void triagedToQueued() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                JourneyEntity journey = createJourneyInState(JourneyState.TRIAGED);
                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity result = stateMachine.transition(journey, JourneyState.QUEUED);

                assertThat(result.getState()).isEqualTo(JourneyState.QUEUED);
            }
        }

        @Test
        @DisplayName("QUEUED -> IN_SERVICE is valid")
        void queuedToInService() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                JourneyEntity journey = createJourneyInState(JourneyState.QUEUED);
                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity result = stateMachine.transition(journey, JourneyState.IN_SERVICE);

                assertThat(result.getState()).isEqualTo(JourneyState.IN_SERVICE);
            }
        }

        @Test
        @DisplayName("IN_SERVICE -> ADMITTED is valid")
        void inServiceToAdmitted() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                JourneyEntity journey = createJourneyInState(JourneyState.IN_SERVICE);
                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity result = stateMachine.transition(journey, JourneyState.ADMITTED);

                assertThat(result.getState()).isEqualTo(JourneyState.ADMITTED);
            }
        }

        @Test
        @DisplayName("IN_SERVICE -> DISCHARGE_PENDING is valid")
        void inServiceToDischargePending() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                JourneyEntity journey = createJourneyInState(JourneyState.IN_SERVICE);
                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity result = stateMachine.transition(journey, JourneyState.DISCHARGE_PENDING);

                assertThat(result.getState()).isEqualTo(JourneyState.DISCHARGE_PENDING);
            }
        }

        @Test
        @DisplayName("ADMITTED -> DEATH_RECORDED is valid")
        void admittedToDeathRecorded() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                JourneyEntity journey = createJourneyInState(JourneyState.ADMITTED);
                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity result = stateMachine.transition(journey, JourneyState.DEATH_RECORDED);

                assertThat(result.getState()).isEqualTo(JourneyState.DEATH_RECORDED);
            }
        }

        @Test
        @DisplayName("ADMITTED -> TRANSFERRED is valid")
        void admittedToTransferred() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                JourneyEntity journey = createJourneyInState(JourneyState.ADMITTED);
                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity result = stateMachine.transition(journey, JourneyState.TRANSFERRED);

                assertThat(result.getState()).isEqualTo(JourneyState.TRANSFERRED);
            }
        }

        @Test
        @DisplayName("DISCHARGE_PENDING -> DISCHARGED is valid")
        void dischargePendingToDischarged() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                JourneyEntity journey = createJourneyInState(JourneyState.DISCHARGE_PENDING);
                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity result = stateMachine.transition(journey, JourneyState.DISCHARGED);

                assertThat(result.getState()).isEqualTo(JourneyState.DISCHARGED);
            }
        }

        @Test
        @DisplayName("TRANSFERRED -> ADMITTED is valid (patient moved to new ward)")
        void transferredToAdmitted() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                JourneyEntity journey = createJourneyInState(JourneyState.TRANSFERRED);
                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity result = stateMachine.transition(journey, JourneyState.ADMITTED);

                assertThat(result.getState()).isEqualTo(JourneyState.ADMITTED);
            }
        }
    }

    @Nested
    @DisplayName("Invalid State Transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("DISCHARGED -> any state throws IllegalStateException")
        void dischargedToAnything() {
            JourneyEntity journey = createJourneyInState(JourneyState.DISCHARGED);

            assertThatThrownBy(() -> stateMachine.transition(journey, JourneyState.ARRIVED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid journey transition");

            assertThatThrownBy(() -> stateMachine.transition(journey, JourneyState.ADMITTED))
                    .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(() -> stateMachine.transition(journey, JourneyState.IN_SERVICE))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ARRIVED -> ADMITTED throws IllegalStateException (must go through triage/queue)")
        void arrivedToAdmitted() {
            JourneyEntity journey = createJourneyInState(JourneyState.ARRIVED);

            assertThatThrownBy(() -> stateMachine.transition(journey, JourneyState.ADMITTED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid journey transition")
                    .hasMessageContaining("ARRIVED")
                    .hasMessageContaining("ADMITTED");
        }

        @Test
        @DisplayName("DEATH_RECORDED -> any state throws IllegalStateException (terminal)")
        void deathRecordedToAnything() {
            JourneyEntity journey = createJourneyInState(JourneyState.DEATH_RECORDED);

            assertThatThrownBy(() -> stateMachine.transition(journey, JourneyState.DISCHARGED))
                    .isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(() -> stateMachine.transition(journey, JourneyState.ADMITTED))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("NO_SHOW -> any state throws IllegalStateException (terminal)")
        void noShowToAnything() {
            JourneyEntity journey = createJourneyInState(JourneyState.NO_SHOW);

            assertThatThrownBy(() -> stateMachine.transition(journey, JourneyState.QUEUED))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("CANCELLED -> any state throws IllegalStateException (terminal)")
        void cancelledToAnything() {
            JourneyEntity journey = createJourneyInState(JourneyState.CANCELLED);

            assertThatThrownBy(() -> stateMachine.transition(journey, JourneyState.ARRIVED))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Journey Creation")
    class JourneyCreation {

        @Test
        @DisplayName("createJourney creates a journey in ARRIVED state")
        void createJourneyInArrivedState() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity journey = stateMachine.createJourney(
                        FACILITY_ID, "CPID-001", "SELF", null);

                assertThat(journey).isNotNull();
                assertThat(journey.getState()).isEqualTo(JourneyState.ARRIVED);
                assertThat(journey.getPatientCpid()).isEqualTo("CPID-001");
                assertThat(journey.getFacilityId()).isEqualTo(FACILITY_ID);
                assertThat(journey.getTenantId()).isEqualTo(TENANT_ID);
                assertThat(journey.getJourneyId()).isNotNull().hasSize(26); // ULID length

                verify(journeyRepository).save(any(JourneyEntity.class));
                verify(outboxRepository).save(any(EventOutboxEntity.class));
            }
        }

        @Test
        @DisplayName("createJourney generates unique ULID identifiers")
        void uniqueJourneyIds() {
            try (MockedStatic<TrustContextHolder> mockedHolder = mockStatic(TrustContextHolder.class)) {
                mockedHolder.when(TrustContextHolder::require).thenReturn(createTrustContext());

                when(journeyRepository.save(any(JourneyEntity.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                JourneyEntity j1 = stateMachine.createJourney(FACILITY_ID, "CPID-001", null, null);
                JourneyEntity j2 = stateMachine.createJourney(FACILITY_ID, "CPID-002", null, null);

                assertThat(j1.getJourneyId()).isNotEqualTo(j2.getJourneyId());
            }
        }
    }
}
