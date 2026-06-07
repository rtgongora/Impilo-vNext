package zw.gov.mohcc.impilo.madi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.NhumeIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.AdverseTransfusionReactionEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionEpisodeEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.AdverseTransfusionReactionRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.HaemovigilanceCaseRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionEpisodeRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HaemovigilanceServiceTest {

    @Mock private AdverseTransfusionReactionRepository reactionRepository;
    @Mock private HaemovigilanceCaseRepository caseRepository;
    @Mock private TransfusionEpisodeRepository episodeRepository;
    @Mock private NhumeIntegration nhumeIntegration;
    @Mock private MadiEventEmitter eventEmitter;

    private HaemovigilanceService service;
    private UUID tenantId;
    private UUID episodeId;

    @BeforeEach
    void setUp() {
        service = new HaemovigilanceService(
                reactionRepository, caseRepository, episodeRepository, nhumeIntegration, eventEmitter);
        tenantId = UUID.randomUUID();
        episodeId = UUID.randomUUID();
    }

    @Test
    void reportReactionPersistsForKnownEpisode() {
        TransfusionEpisodeEntity episode = new TransfusionEpisodeEntity();
        episode.setEpisodeId(episodeId);
        when(episodeRepository.findByEpisodeIdAndTenantId(episodeId, tenantId))
                .thenReturn(Optional.of(episode));
        when(reactionRepository.save(any())).thenAnswer(invocation -> {
            AdverseTransfusionReactionEntity entity = invocation.getArgument(0);
            if (entity.getReactionId() == null) {
                entity.setReactionId(UUID.randomUUID());
            }
            return entity;
        });

        var reaction = service.reportReaction(
                tenantId, episodeId, "FEVER", "MODERATE", "Temp spike", "nurse-1", UUID.randomUUID());

        assertThat(reaction.getReactionType()).isEqualTo("FEVER");
        assertThat(reaction.getSeverity()).isEqualTo("MODERATE");
    }
}
