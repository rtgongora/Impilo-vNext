package zw.gov.mohcc.impilo.simba.social;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.social.core.SocialNotificationService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialNotificationEventEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialNotificationEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Notifications write a persisted event AND emit an outbox event (the Khuluma delivery seam). */
class SocialNotificationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000040");

    private final SocialNotificationEventRepository repo = mock(SocialNotificationEventRepository.class);
    private final SimbaEventEmitter events = mock(SimbaEventEmitter.class);

    private SocialNotificationService svc() {
        when(repo.save(any(SocialNotificationEventEntity.class))).thenAnswer(i -> {
            SocialNotificationEventEntity n = i.getArgument(0);
            if (n.getNotificationId() == null) n.setNotificationId(UUID.randomUUID());
            return n;
        });
        return new SocialNotificationService(repo, events);
    }

    @Test
    void notifyWritesEventAndEmitsOutbox() {
        SocialNotificationEventEntity n = svc().notify(TENANT,
                new SocialNotificationService.NotifyRequest("recipient", "actor", "COMMENT",
                        "POST", UUID.randomUUID(), "Someone commented on your post"));

        verify(repo).save(any(SocialNotificationEventEntity.class));
        assertThat(n.getRecipientCpid()).isEqualTo("recipient");
        assertThat(n.getStatus()).isEqualTo("UNREAD");
        verify(events).emit(eq(TENANT), eq("WELLNESS_SOCIAL_NOTIFICATION"), anyString(),
                anyString(), eq("recipient"), anyString(), any());
    }

    @Test
    void notifyRequiresRecipient() {
        assertThatThrownBy(() -> svc().notify(TENANT,
                new SocialNotificationService.NotifyRequest(null, "actor", "COMMENT",
                        "POST", UUID.randomUUID(), "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventTypeMapping() {
        assertThat(SocialNotificationService.eventTypeFor("ANNOUNCEMENT"))
                .isEqualTo("SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED");
        assertThat(SocialNotificationService.eventTypeFor("CHALLENGE_REMINDER"))
                .isEqualTo("SIMBA_CHALLENGE_REMINDER_REQUESTED");
        assertThat(SocialNotificationService.eventTypeFor("MODERATION"))
                .isEqualTo("SIMBA_MODERATION_ACTION_TAKEN");
        assertThat(SocialNotificationService.eventTypeFor("COMMENT"))
                .isEqualTo("SIMBA_SOCIAL_NOTIFICATION_REQUESTED");
        assertThat(SocialNotificationService.eventTypeFor(null))
                .isEqualTo("SIMBA_SOCIAL_NOTIFICATION_REQUESTED");
    }

    @Test
    void markReadSetsReadState() {
        UUID id = UUID.randomUUID();
        SocialNotificationEventEntity existing = new SocialNotificationEventEntity();
        existing.setTenantId(TENANT);
        existing.setNotificationId(id);
        existing.setRecipientCpid("recipient");
        existing.setNotificationType("COMMENT");
        when(repo.findByNotificationIdAndTenantId(id, TENANT))
                .thenReturn(java.util.Optional.of(existing));
        when(repo.save(any(SocialNotificationEventEntity.class))).thenAnswer(i -> i.getArgument(0));

        SocialNotificationEventEntity n = new SocialNotificationService(repo, events).markRead(TENANT, id);
        assertThat(n.getStatus()).isEqualTo("READ");
        assertThat(n.getReadAt()).isNotNull();

        ArgumentCaptor<SocialNotificationEventEntity> captor =
                ArgumentCaptor.forClass(SocialNotificationEventEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("READ");
    }
}
