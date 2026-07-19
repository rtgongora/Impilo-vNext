package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Care-first biometric patient check-in through the shared ABIS seam: a probe on
 * {@code createJourney} is verified against the subject's enrolled template.
 * MATCH → arrival marked {@code biometricVerified}; NO_MATCH → rejected, nothing
 * persisted; engine UNAVAILABLE → the check-in still proceeds (a biometric outage
 * must never block a patient arriving for care) but is not recorded as a match.
 */
@ExtendWith(MockitoExtension.class)
class JourneyStateMachineBiometricTest {

    @Mock private JourneyRepository journeyRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private TelemetryService telemetryService;
    @Mock private BiometricVerificationClient biometricVerification;

    private JourneyStateMachine stateMachine;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final String ACTOR_ID = "nurse-42";
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stateMachine = new JourneyStateMachine(
                journeyRepository, outboxRepository, telemetryService, new ObjectMapper(), biometricVerification);
        lenient().when(journeyRepository.save(any(JourneyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TrustContext trustContext() {
        return new TrustContext(TENANT_ID, ACTOR_ID, "PROVIDER", "TREATMENT",
                null, CORRELATION_ID, FACILITY_ID, null, null, AccessMode.INTERNAL);
    }

    @Test
    @DisplayName("MATCH → journey created and marked biometricVerified")
    void matchMarksVerified() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(trustContext());
            when(biometricVerification.verify(eq("CPID-001"), eq("FINGERPRINT"), any()))
                    .thenReturn(new BiometricVerificationClient.Decision("MATCH", 0.98));

            JourneyEntity journey = stateMachine.createJourney(
                    FACILITY_ID, "CPID-001", "SELF", null, null,
                    "CPID-001", "FINGERPRINT", "cHJvYmU=");

            assertThat(journey.getState()).isEqualTo(JourneyState.ARRIVED);
            ArgumentCaptor<JourneyEntity> captor = ArgumentCaptor.forClass(JourneyEntity.class);
            verify(journeyRepository).save(captor.capture());
            assertThat(captor.getValue().isBiometricVerified()).isTrue();
        }
    }

    @Test
    @DisplayName("NO_MATCH → rejected, nothing persisted")
    void noMatchRejected() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(trustContext());
            when(biometricVerification.verify(any(), any(), any()))
                    .thenReturn(new BiometricVerificationClient.Decision("NO_MATCH", 0.05));

            assertThatThrownBy(() -> stateMachine.createJourney(
                    FACILITY_ID, "CPID-001", "SELF", null, null,
                    "CPID-001", "FINGERPRINT", "cHJvYmU="))
                    .isInstanceOf(PctDomainException.class)
                    .hasMessageContaining("biometric verification failed");

            verify(journeyRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("engine UNAVAILABLE → check-in proceeds, not marked biometric (care-first)")
    void unavailableFallsBack() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(trustContext());
            when(biometricVerification.verify(any(), any(), any()))
                    .thenReturn(BiometricVerificationClient.Decision.unavailable());

            JourneyEntity journey = stateMachine.createJourney(
                    FACILITY_ID, "CPID-001", "SELF", null, null,
                    "CPID-001", "FINGERPRINT", "cHJvYmU=");

            assertThat(journey.getState()).isEqualTo(JourneyState.ARRIVED);
            ArgumentCaptor<JourneyEntity> captor = ArgumentCaptor.forClass(JourneyEntity.class);
            verify(journeyRepository).save(captor.capture());
            assertThat(captor.getValue().isBiometricVerified())
                    .as("a biometric outage must not be recorded as a match")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("no probe supplied → unchanged non-biometric check-in, ABIS never called")
    void noProbeUnchanged() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(trustContext());

            JourneyEntity journey = stateMachine.createJourney(
                    FACILITY_ID, "CPID-001", "SELF", null, null);

            assertThat(journey.isBiometricVerified()).isFalse();
            verifyNoInteractions(biometricVerification);
        }
    }
}
