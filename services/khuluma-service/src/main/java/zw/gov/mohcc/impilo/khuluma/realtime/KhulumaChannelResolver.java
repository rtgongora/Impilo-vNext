package zw.gov.mohcc.impilo.khuluma.realtime;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.khuluma.repository.CallParticipantRepository;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationParticipantRepository;
import zw.gov.mohcc.impilo.realtime.RealtimeChannels;
import zw.gov.mohcc.impilo.realtime.SubscriptionChannelResolver;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Khuluma membership-backed channel resolver. Conversation/call access checks stay here because
 * they are JPA-coupled to khuluma tables; the shared gateway only sees
 * {@link SubscriptionChannelResolver}.
 */
@Component
public class KhulumaChannelResolver implements SubscriptionChannelResolver {

    private final ConversationParticipantRepository participants;
    private final CallParticipantRepository callParticipants;

    public KhulumaChannelResolver(ConversationParticipantRepository participants,
                                  CallParticipantRepository callParticipants) {
        this.participants = participants;
        this.callParticipants = callParticipants;
    }

    @Override
    public Set<String> resolveFor(UUID tenantId, String actorId, String requested) {
        Set<String> channels = new LinkedHashSet<>();
        channels.add(RealtimeChannels.actorChannel(actorId));
        channels.add(RealtimeChannels.tenantChannel(tenantId));
        participants.findByTenantIdAndActorIdAndActiveTrue(tenantId, actorId)
                .forEach(p -> channels.add(RealtimeChannels.conversationChannel(p.getConversationId())));

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
        if (channel.startsWith("conversation:")) {
            UUID conversationId = RealtimeChannels.parseUuidSuffix(channel, "conversation:");
            return conversationId != null
                    && participants.existsByConversationIdAndActorIdAndActiveTrue(conversationId, actorId);
        }
        if (channel.startsWith("call:")) {
            UUID callId = RealtimeChannels.parseUuidSuffix(channel, "call:");
            return callId != null && callParticipants.findByCallIdAndActorId(callId, actorId).isPresent();
        }
        return false;
    }
}
