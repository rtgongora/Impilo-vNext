package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.domain.TransfusionStatus;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.ButanoIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionEpisodeEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionEpisodeRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionObservationRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionOutcomeRepository;
import zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Biometric pre-transfusion patient-identity interlock through the shared verification
 * seam: MATCH → recorded as BIOMETRIC with the confirmed subject ref; NO_MATCH → the
 * bedside step is rejected (wrong-patient safety stop, nothing persisted); engine
 * UNAVAILABLE → falls back to the declared two-person/manual method so a needed
 * transfusion is never blocked by a biometric outage.
 */
@ExtendWith(MockitoExtension.class)
class TransfusionServiceBiometricTest {

    @Mock private TransfusionEpisodeRepository episodeRepository;
    @Mock private TransfusionObservationRepository observationRepository;
    @Mock private TransfusionOutcomeRepository outcomeRepository;
    @Mock private ButanoIntegration butanoIntegration;
    @Mock private MadiEventEmitter eventEmitter;
    @Mock private zw.gov.mohcc.impilo.madi.persistence.repository.BloodOrderRepository orderRepository;
    @Mock private zw.gov.mohcc.impilo.madi.integration.OrosIntegration orosIntegration;
    @Mock private BiometricVerificationClient biometricVerification;

    private TransfusionService transfusionService;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transfusionService = new TransfusionService(episodeRepository, observationRepository,
                outcomeRepository, butanoIntegration, eventEmitter, orderRepository, orosIntegration,
                biometricVerification);
    }

    private TransfusionEpisodeEntity inProgressEpisode(UUID episodeId) {
        TransfusionEpisodeEntity episode = new TransfusionEpisodeEntity();
        episode.setEpisodeId(episodeId);
        episode.setPatientCpid("CPID-1");
        episode.setStatus(TransfusionStatus.IN_PROGRESS.name());
        when(episodeRepository.findByEpisodeIdAndTenantId(episodeId, TENANT_ID)).thenReturn(Optional.of(episode));
        return episode;
    }

    @Test
    @DisplayName("MATCH → patient identity confirmed by biometric")
    void matchConfirmsPatientByBiometric() {
        UUID episodeId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        inProgressEpisode(episodeId);
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(biometricVerification.verify(eq("subj-patient-1"), eq("FINGERPRINT"), any()))
                .thenReturn(new BiometricVerificationClient.Decision("MATCH", 0.98));

        TransfusionEpisodeEntity result = transfusionService.verifyPreTransfusion(
                TENANT_ID, episodeId, "CPID-1", unitId,
                "MANUAL_GOVERNED", null, "BARCODE_SCAN", "scan-1", "nurse-1",
                "subj-patient-1", "FINGERPRINT", "cHJvYmU=");

        assertThat(result.getPatientVerified()).isTrue();
        assertThat(result.getPatientVerificationMethod()).isEqualTo("BIOMETRIC");
        assertThat(result.getPatientBiometricRef()).isEqualTo("subj-patient-1");
    }

    @Test
    @DisplayName("NO_MATCH → wrong-patient safety stop, nothing persisted")
    void noMatchRejectsBedsideStep() {
        UUID episodeId = UUID.randomUUID();
        inProgressEpisode(episodeId);
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(new BiometricVerificationClient.Decision("NO_MATCH", 0.05));

        assertThatThrownBy(() -> transfusionService.verifyPreTransfusion(
                TENANT_ID, episodeId, "CPID-1", UUID.randomUUID(),
                "MANUAL_GOVERNED", null, "BARCODE_SCAN", "scan-1", "nurse-1",
                "subj-patient-1", "FINGERPRINT", "cHJvYmU="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wrong-patient");

        verify(episodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("engine UNAVAILABLE → falls back to declared manual method")
    void unavailableFallsBackToManual() {
        UUID episodeId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        inProgressEpisode(episodeId);
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(biometricVerification.verify(any(), any(), any()))
                .thenReturn(BiometricVerificationClient.Decision.unavailable());

        TransfusionEpisodeEntity result = transfusionService.verifyPreTransfusion(
                TENANT_ID, episodeId, "CPID-1", unitId,
                "MANUAL_GOVERNED", null, "BARCODE_SCAN", "scan-1", "nurse-1",
                "subj-patient-1", "FINGERPRINT", "cHJvYmU=");

        assertThat(result.getPatientVerified()).isTrue();
        assertThat(result.getPatientVerificationMethod())
                .as("not marked biometric on an outage")
                .isEqualTo("MANUAL_GOVERNED");
    }
}
