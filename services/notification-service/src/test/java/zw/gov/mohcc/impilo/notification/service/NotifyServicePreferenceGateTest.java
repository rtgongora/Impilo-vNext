package zw.gov.mohcc.impilo.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyResponse;
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.integration.MvumoPreferenceClient;
import zw.gov.mohcc.impilo.notification.repository.NotificationRepository;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Mvumo communication-preference gate inside
 * {@link NotifyService#enqueue}.
 *
 * <p>When Mvumo says the patient has opted out of a channel/message kind,
 * the notification must be persisted as CANCELLED (auditable, never
 * silently dropped) and a {@code preference_blocked} outbox event emitted
 * instead of {@code enqueued}. When Mvumo is unreachable or has no opinion
 * the pipeline fails open (PENDING) — informational messages must not be
 * lost to a preference-service outage.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotifyServicePreferenceGateTest {

    private static final String TENANT = "moh-zw";
    private static final String CPID = "CPID-ZW-00042";

    @Mock private NotificationRepository notificationRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private MvumoPreferenceClient mvumoPreferenceClient;
    @Mock private NotificationRecipientResolver recipientResolver;

    private NotifyService service;

    @BeforeEach
    void setUp() {
        service = new NotifyService(
                notificationRepository, outboxEventRepository, new ObjectMapper(),
                mvumoPreferenceClient, recipientResolver);
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private NotifyRequest queueRequest() {
        // Shape produced by QueueLifecycleNotificationConsumer: IN_APP to the
        // CPID inbox, patientRef = CPID, messageKind REMINDER.
        return new NotifyRequest(
                "QUEUE_CITIZEN_CALLED", "IN_APP", CPID,
                Map.of("token", "12"), CPID, "REMINDER", CPID);
    }

    private RequestContext ctx() {
        return RequestContext.of(TENANT, "national", null, null, null, null, null);
    }

    @Test
    @DisplayName("Mvumo disallow → notification CANCELLED + preference_blocked outbox event")
    void mvumoDisallow_cancelsNotification_andEmitsPreferenceBlockedEvent() {
        when(mvumoPreferenceClient.evaluateAllowed(TENANT, CPID, "IN_APP", "REMINDER"))
                .thenReturn(Optional.of(false));

        NotifyResponse response = service.enqueue(queueRequest(), ctx());

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.detail()).contains("Mvumo");

        ArgumentCaptor<NotificationEntity> entityCaptor =
                ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(entityCaptor.capture());
        NotificationEntity entity = entityCaptor.getValue();
        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        assertThat(entity.getLastError()).contains("Mvumo communication preferences");

        ArgumentCaptor<OutboxEventEntity> outboxCaptor =
                ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity outbox = outboxCaptor.getValue();
        assertThat(outbox.getEventType())
                .isEqualTo("impilo.notify.notification.preference_blocked.v1");
        assertThat(outbox.getPayloadJson())
                .contains("\"reason\":\"preference_gate\"")
                .contains("\"status\":\"CANCELLED\"");
    }

    @Test
    @DisplayName("Mvumo allow → notification PENDING + enqueued outbox event")
    void mvumoAllow_keepsNotificationPending() {
        when(mvumoPreferenceClient.evaluateAllowed(TENANT, CPID, "IN_APP", "REMINDER"))
                .thenReturn(Optional.of(true));

        NotifyResponse response = service.enqueue(queueRequest(), ctx());

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.detail()).isNull();

        ArgumentCaptor<OutboxEventEntity> outboxCaptor =
                ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType())
                .isEqualTo("impilo.notify.notification.enqueued.v1");
    }

    @Test
    @DisplayName("Mvumo has no opinion (empty) → fail-open, notification PENDING")
    void mvumoUnavailable_failsOpenToPending() {
        when(mvumoPreferenceClient.evaluateAllowed(TENANT, CPID, "IN_APP", "REMINDER"))
                .thenReturn(Optional.empty());

        NotifyResponse response = service.enqueue(queueRequest(), ctx());

        assertThat(response.status()).isEqualTo("PENDING");
    }
}
