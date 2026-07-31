package zw.gov.mohcc.impilo.pct.realtime;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.realtime.RealtimeChannels;
import zw.gov.mohcc.impilo.realtime.SubscriptionChannelResolver;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * PCT membership-backed channel resolver for emergency episode invalidation hints.
 * Admits {@code actor:}, {@code tenant:}, and {@code episode:} channels only when the episode
 * exists in the actor's tenant — never clinical payload, never cross-tenant.
 */
@Component
public class EmergencyChannelResolver implements SubscriptionChannelResolver {

    private final EmergencyEpisodeRepository episodes;

    public EmergencyChannelResolver(EmergencyEpisodeRepository episodes) {
        this.episodes = episodes;
    }

    @Override
    public Set<String> resolveFor(UUID tenantId, String actorId, String requested) {
        Set<String> channels = new LinkedHashSet<>();
        channels.add(RealtimeChannels.actorChannel(actorId));
        channels.add(RealtimeChannels.tenantChannel(tenantId));

        if (requested != null && !requested.isBlank()) {
            for (String raw : requested.split(",")) {
                String channel = raw.trim();
                if (!channel.isEmpty() && mayAccess(tenantId, actorId, channel)) {
                    channels.add(channel);
                }
            }
        }
        return channels;
    }

    private boolean mayAccess(UUID tenantId, String actorId, String channel) {
        if (channel.equals(RealtimeChannels.actorChannel(actorId))
                || channel.equals(RealtimeChannels.tenantChannel(tenantId))) {
            return true;
        }
        if (channel.startsWith("episode:")) {
            UUID episodeId = RealtimeChannels.parseUuidSuffix(channel, "episode:");
            return episodeId != null && episodes.findByEpisodeIdAndTenantId(episodeId, tenantId).isPresent();
        }
        return false;
    }
}
