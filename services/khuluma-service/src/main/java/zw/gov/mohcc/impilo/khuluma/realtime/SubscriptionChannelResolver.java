package zw.gov.mohcc.impilo.khuluma.realtime;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.khuluma.repository.CallParticipantRepository;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationParticipantRepository;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Computes the set of realtime channels a connecting actor may subscribe to. Always includes the
 * actor's personal channel, the tenant broadcast channel, and every conversation the actor is an
 * active participant of. Explicitly-requested channels are admitted only after an access check, so
 * a subscriber can never receive a conversation/call they are not a member of (tenant isolation is
 * additionally enforced at dispatch time by {@link RealtimeHub}).
 */
@Component
public class SubscriptionChannelResolver {

    private final ConversationParticipantRepository participants;
    private final CallParticipantRepository callParticipants;

    public SubscriptionChannelResolver(ConversationParticipantRepository participants,
                                       CallParticipantRepository callParticipants) {
        this.participants = participants;
        this.callParticipants = callParticipants;
    }

    public static String actorChannel(String actorId) { return "actor:" + actorId; }
    public static String tenantChannel(UUID tenantId) { return "tenant:" + tenantId; }
    public static String conversationChannel(UUID conversationId) { return "conversation:" + conversationId; }

    /** The default channel set for an actor: personal + tenant broadcast + active conversations. */
    public Set<String> resolveFor(UUID tenantId, String actorId, String requested) {
        Set<String> channels = new LinkedHashSet<>();
        channels.add(actorChannel(actorId));
        channels.add(tenantChannel(tenantId));
        participants.findByTenantIdAndActorIdAndActiveTrue(tenantId, actorId)
                .forEach(p -> channels.add(conversationChannel(p.getConversationId())));

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

    /** Whether the actor is permitted to subscribe to an explicitly-requested channel. */
    private boolean mayAccess(UUID tenantId, String actorId, String channel) {
        if (channel.equals(actorChannel(actorId)) || channel.equals(tenantChannel(tenantId))) {
            return true;
        }
        if (channel.startsWith("conversation:")) {
            UUID conversationId = parse(channel.substring("conversation:".length()));
            return conversationId != null
                    && participants.existsByConversationIdAndActorIdAndActiveTrue(conversationId, actorId);
        }
        if (channel.startsWith("call:")) {
            UUID callId = parse(channel.substring("call:".length()));
            return callId != null && callParticipants.findByCallIdAndActorId(callId, actorId).isPresent();
        }
        return false;
    }

    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
