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
class TransfusionServiceTest {

    @Mock private TransfusionEpisodeRepository episodeRepository;
    @Mock private TransfusionObservationRepository observationRepository;
    @Mock private TransfusionOutcomeRepository outcomeRepository;
    @Mock private ButanoIntegration butanoIntegration;
    @Mock private MadiEventEmitter eventEmitter;

    private TransfusionService transfusionService;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transfusionService = new TransfusionService(episodeRepository, observationRepository,
                outcomeRepository, butanoIntegration, eventEmitter);
    }

    @Test
    void startEpisode_setsInProgress() {
        when(episodeRepository.save(any())).thenAnswer(inv -> {
            TransfusionEpisodeEntity e = inv.getArgument(0);
            e.setEpisodeId(UUID.randomUUID());
            return e;
        });

        TransfusionEpisodeEntity episode = transfusionService.startEpisode(
                TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), "CPID-1", "nurse-1", UUID.randomUUID(), null);

        assertThat(episode.getStatus()).isEqualTo(TransfusionStatus.IN_PROGRESS.name());
        verify(eventEmitter).emit(eq("TRANSFUSION"), anyString(), eq("TRANSFUSION_STARTED"), anyString(), anyString(), anyMap(), eq(TENANT_ID));
    }

    @Test
    void verify_requiresCompletedStatus() {
        UUID episodeId = UUID.randomUUID();
        TransfusionEpisodeEntity episode = new TransfusionEpisodeEntity();
        episode.setEpisodeId(episodeId);
        episode.setStatus(TransfusionStatus.IN_PROGRESS.name());
        when(episodeRepository.findByEpisodeIdAndTenantId(episodeId, TENANT_ID)).thenReturn(Optional.of(episode));

        assertThatThrownBy(() -> transfusionService.verify(TENANT_ID, episodeId, "supervisor-1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
