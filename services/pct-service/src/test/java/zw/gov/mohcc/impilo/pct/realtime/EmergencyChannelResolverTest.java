package zw.gov.mohcc.impilo.pct.realtime;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.realtime.RealtimeChannels;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmergencyChannelResolverTest {

    @Test
    void admitsEpisodeChannelOnlyWhenEpisodeExistsInTenant() {
        EmergencyEpisodeRepository repo = mock(EmergencyEpisodeRepository.class);
        EmergencyChannelResolver resolver = new EmergencyChannelResolver(repo);

        UUID tenant = UUID.randomUUID();
        UUID episode = UUID.randomUUID();
        when(repo.findByEpisodeIdAndTenantId(episode, tenant))
                .thenReturn(Optional.of(new EmergencyEpisodeEntity()));

        Set<String> channels = resolver.resolveFor(tenant, "provider-1",
                RealtimeChannels.episodeChannel(episode) + "," + RealtimeChannels.episodeChannel(UUID.randomUUID()));

        assertThat(channels).contains(
                RealtimeChannels.actorChannel("provider-1"),
                RealtimeChannels.tenantChannel(tenant),
                RealtimeChannels.episodeChannel(episode));
        assertThat(channels.stream().filter(c -> c.startsWith("episode:")).count()).isEqualTo(1);
    }
}
