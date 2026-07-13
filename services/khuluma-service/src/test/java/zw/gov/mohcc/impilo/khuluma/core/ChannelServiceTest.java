package zw.gov.mohcc.impilo.khuluma.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.khuluma.core.ChannelService.NewChannel;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationParticipantEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MessageEntity;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationParticipantRepository;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationRepository;
import zw.gov.mohcc.impilo.khuluma.repository.MessageRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

    private static final UUID T = UUID.randomUUID();

    @Mock private ConversationRepository conversations;
    @Mock private ConversationParticipantRepository participants;
    @Mock private MessageRepository messages;
    @Mock private OutboxAppender outbox;
    @Mock private RealtimeDispatcher realtime;

    private ChannelService service() {
        return new ChannelService(conversations, participants, messages, outbox, realtime);
    }

    @BeforeEach
    void echoSaves() {
        lenient().when(conversations.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(participants.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ConversationEntity channel(String type, String scopeType) {
        ConversationEntity c = new ConversationEntity();
        c.setTenantId(T);
        c.setConversationType(type);
        c.setScopeType(scopeType);
        c.setStatus("ACTIVE");
        return c;
    }

    private ConversationParticipantEntity participant(String role, boolean active) {
        ConversationParticipantEntity p = new ConversationParticipantEntity();
        p.setRole(role);
        p.setActive(active);
        return p;
    }

    @Test
    void create_scopes_the_channel_and_makes_creator_owner() {
        when(participants.findByConversationIdAndActorId(any(), any())).thenReturn(Optional.empty());

        ConversationEntity c = service().create(T, "prov-1", "PROVIDER",
                new NewChannel("channel", "facility", UUID.randomUUID(), "Ward 4 Team", "Coordination"));

        assertThat(c.getConversationType()).isEqualTo("CHANNEL");
        assertThat(c.getScopeType()).isEqualTo("FACILITY");
        // creator becomes an OWNER participant.
        org.mockito.ArgumentCaptor<ConversationParticipantEntity> cap =
                org.mockito.ArgumentCaptor.forClass(ConversationParticipantEntity.class);
        org.mockito.Mockito.verify(participants).save(cap.capture());
        assertThat(cap.getValue().getRole()).isEqualTo("OWNER");
        assertThat(cap.getValue().isActive()).isTrue();
    }

    @Test
    void create_requires_a_scope_type() {
        assertThatThrownBy(() -> service().create(T, "p", "PROVIDER",
                new NewChannel("channel", "BOGUS", UUID.randomUUID(), "t", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scopeType");
    }

    @Test
    void join_is_idempotent_and_reactivates_a_prior_membership() {
        UUID conv = UUID.randomUUID();
        when(conversations.findByConversationIdAndTenantId(conv, T)).thenReturn(Optional.of(channel("CHANNEL", "FACILITY")));
        when(participants.findByConversationIdAndActorId(any(), any()))
                .thenReturn(Optional.of(participant("MEMBER", false))); // previously left

        ConversationParticipantEntity p = service().join(T, conv, "client-1", "CITIZEN", "Tariro");

        assertThat(p.isActive()).isTrue();
        assertThat(p.getLeftAt()).isNull();
    }

    @Test
    void leave_sets_membership_inactive() {
        UUID conv = UUID.randomUUID();
        ConversationParticipantEntity p = participant("MEMBER", true);
        when(conversations.findByConversationIdAndTenantId(conv, T)).thenReturn(Optional.of(channel("CHANNEL", "FACILITY")));
        when(participants.findByConversationIdAndActorId(conv, "client-1")).thenReturn(Optional.of(p));

        service().leave(T, conv, "client-1");

        assertThat(p.isActive()).isFalse();
        assertThat(p.getLeftAt()).isNotNull();
    }

    @Test
    void only_owner_or_moderator_may_broadcast() {
        UUID conv = UUID.randomUUID();
        when(conversations.findByConversationIdAndTenantId(conv, T)).thenReturn(Optional.of(channel("CHANNEL", "FACILITY")));

        // an OWNER may broadcast
        when(participants.findByConversationIdAndActorId(conv, "owner-1")).thenReturn(Optional.of(participant("OWNER", true)));
        MessageEntity msg = service().broadcast(T, conv, "owner-1", "PROVIDER", "Clinic closed tomorrow");
        assertThat(msg.getBody()).isEqualTo("Clinic closed tomorrow");
        assertThat(msg.getContentType()).isEqualTo("BROADCAST");

        // a plain MEMBER may not
        when(participants.findByConversationIdAndActorId(conv, "member-1")).thenReturn(Optional.of(participant("MEMBER", true)));
        assertThatThrownBy(() -> service().broadcast(T, conv, "member-1", "CITIZEN", "spam"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void discover_lists_channels_for_a_scope() {
        UUID fac = UUID.randomUUID();
        when(conversations.findByTenantIdAndScopeTypeAndScopeRefAndStatusOrderByCreatedAtDesc(T, "FACILITY", fac, "ACTIVE"))
                .thenReturn(List.of(channel("CHANNEL", "FACILITY")));

        assertThat(service().discover(T, "facility", fac)).hasSize(1);
    }
}
