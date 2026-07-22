package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;
import zw.gov.mohcc.impilo.experience.events.TelemedicineCommunicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * TM-B9: a scheduled teleconsult enqueues FUTURE-DATED reminders (with notBefore) that
 * notification-service holds until due, in addition to the immediate reminder.
 */
class TelemedicineCommsScheduledReminderTest {

    private final NotificationServiceClient notificationClient = Mockito.mock(NotificationServiceClient.class);
    private final SupportServiceClient supportClient = Mockito.mock(SupportServiceClient.class);
    private final TelemedicineCommunicationEventPublisher eventPublisher = Mockito.mock(TelemedicineCommunicationEventPublisher.class);
    private final TelemedicineCommsMetricsStore metricsStore = Mockito.mock(TelemedicineCommsMetricsStore.class);
    private final TelemedicineWorkflowTelemetry workflowTelemetry = Mockito.mock(TelemedicineWorkflowTelemetry.class);

    private final TelemedicineCommsWorkflowService service = new TelemedicineCommsWorkflowService(
            notificationClient, supportClient, eventPublisher, metricsStore, workflowTelemetry);

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> capturedBodies() {
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(notificationClient, Mockito.atLeastOnce()).sendNotification(cap.capture());
        return cap.getAllValues();
    }

    @Test
    void scheduledEvent_enqueuesFutureDatedReminders_withNotBefore() {
        String scheduledAt = OffsetDateTime.now().plusDays(2).toString();
        service.processLifecycleEvent("telemedicine.session.scheduled",
                Map.of("patientCpid", "CPID-1", "sessionId", "ref-1", "scheduledAt", scheduledAt));

        var bodies = capturedBodies();
        // Immediate reminder + T-24h + T-1h + T-15m device-check = at least 4 sends.
        assertThat(bodies).hasSizeGreaterThanOrEqualTo(4);
        // The future-dated ones carry notBefore; the immediate one does not.
        assertThat(bodies).anyMatch(b -> "TELECONSULT_REMINDER".equals(b.get("templateKey")) && b.get("notBefore") != null);
        assertThat(bodies).anyMatch(b -> "TELECONSULT_DEVICE_CHECK".equals(b.get("templateKey")) && b.get("notBefore") != null);
    }

    @Test
    void scheduledEventInThePast_doesNotEnqueueStaleReminders() {
        // scheduledAt already passed → the T-minus reminders are all in the past and must be skipped.
        String scheduledAt = OffsetDateTime.now().minusHours(2).toString();
        service.processLifecycleEvent("telemedicine.session.scheduled",
                Map.of("patientCpid", "CPID-1", "sessionId", "ref-2", "scheduledAt", scheduledAt));

        var bodies = capturedBodies();
        // Only the immediate reminder — no notBefore-carrying reminders.
        assertThat(bodies).noneMatch(b -> b.get("notBefore") != null);
    }

    @Test
    void completedEvent_enqueuesFeedbackPrompt_withNotBefore() {
        service.processLifecycleEvent("telemedicine.session.completed",
                Map.of("patientCpid", "CPID-1", "sessionId", "ref-3"));

        var bodies = capturedBodies();
        assertThat(bodies).anyMatch(b -> "TELECONSULT_FEEDBACK_PROMPT".equals(b.get("templateKey")) && b.get("notBefore") != null);
    }
}
