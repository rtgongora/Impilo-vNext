package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.domain.TransfusionStatus;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.ButanoIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionEpisodeEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionEpisodeRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionObservationRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionOutcomeRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransfusionPreVerifyTest {

    @Mock private TransfusionEpisodeRepository episodeRepository;
    @Mock private TransfusionObservationRepository observationRepository;
    @Mock private TransfusionOutcomeRepository outcomeRepository;
    @Mock private ButanoIntegration butanoIntegration;
    @Mock private MadiEventEmitter eventEmitter;
    @Mock private zw.gov.mohcc.impilo.madi.persistence.repository.BloodOrderRepository orderRepository;
    @Mock private zw.gov.mohcc.impilo.madi.integration.OrosIntegration orosIntegration;

    private TransfusionService transfusionService;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transfusionService = new TransfusionService(episodeRepository, observationRepository,
                outcomeRepository, butanoIntegration, eventEmitter, orderRepository, orosIntegration,
                new zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient(false, null));
    }

    @Test
    void verifyPreTransfusion_setsPatientAndUnitFlags() {
        UUID episodeId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        TransfusionEpisodeEntity episode = new TransfusionEpisodeEntity();
        episode.setEpisodeId(episodeId);
        episode.setPatientCpid("CPID-1");
        episode.setStatus(TransfusionStatus.IN_PROGRESS.name());
        when(episodeRepository.findByEpisodeIdAndTenantId(episodeId, TENANT_ID)).thenReturn(Optional.of(episode));
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransfusionEpisodeEntity result = transfusionService.verifyPreTransfusion(
                TENANT_ID, episodeId, "CPID-1", unitId,
                "BIOMETRIC", "bio-ref-1", "BARCODE_SCAN", "scan-ref-1", "nurse-1");

        assertThat(result.getPatientVerified()).isTrue();
        assertThat(result.getUnitVerified()).isTrue();
        assertThat(result.getBloodUnitId()).isEqualTo(unitId);
        verify(eventEmitter).emit(eq("TRANSFUSION"), eq(episodeId.toString()), eq("PRE_TRANSFUSION_VERIFIED"),
                anyString(), anyString(), anyMap(), eq(TENANT_ID));
    }

    @Test
    void recordObservation_requiresPreVerification() {
        UUID episodeId = UUID.randomUUID();
        TransfusionEpisodeEntity episode = new TransfusionEpisodeEntity();
        episode.setEpisodeId(episodeId);
        episode.setPatientVerified(false);
        episode.setUnitVerified(false);
        when(episodeRepository.findByEpisodeIdAndTenantId(episodeId, TENANT_ID)).thenReturn(Optional.of(episode));

        assertThatThrownBy(() -> transfusionService.recordObservation(
                TENANT_ID, episodeId, "TEMPERATURE", null, "36.5", "C", "nurse-1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pre-transfusion verification");
    }

    @Test
    void verifyPreTransfusion_rejectsPatientMismatch() {
        UUID episodeId = UUID.randomUUID();
        TransfusionEpisodeEntity episode = new TransfusionEpisodeEntity();
        episode.setEpisodeId(episodeId);
        episode.setPatientCpid("CPID-1");
        episode.setStatus(TransfusionStatus.IN_PROGRESS.name());
        when(episodeRepository.findByEpisodeIdAndTenantId(episodeId, TENANT_ID)).thenReturn(Optional.of(episode));

        assertThatThrownBy(() -> transfusionService.verifyPreTransfusion(
                TENANT_ID, episodeId, "CPID-OTHER", UUID.randomUUID(),
                "MANUAL_GOVERNED", null, "MANUAL_GOVERNED", null, "nurse-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
