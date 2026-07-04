package zw.gov.mohcc.impilo.notification.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.service.NotifyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QueueLifecycleNotificationConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotifyService notifyService;
    private QueueLifecycleNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        notifyService = mock(NotifyService.class);
        consumer = new QueueLifecycleNotificationConsumer(notifyService, objectMapper);
    }

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("pct.queue.item.updated", 0, 0L, null, json);
    }

    @Test
    void enqueuedEventMapsToJoinedTemplateWithToken() throws Exception {
        NotifyRequest request = consumer.toNotifyRequest(objectMapper.readTree("""
                {"eventType":"QUEUE_ITEM_ENQUEUED","tenantId":"t1","queueId":"q1",
                 "journeyId":"J1","patientCpid":"CPID-001","tokenNumber":12,"priority":3}
                """));

        assertThat(request).isNotNull();
        assertThat(request.templateKey()).isEqualTo("QUEUE_CITIZEN_JOINED");
        assertThat(request.channel()).isEqualTo("IN_APP");
        assertThat(request.recipient()).isEqualTo("CPID-001");
        assertThat(request.patientRef()).isEqualTo("CPID-001");
        assertThat(request.messageKind()).isEqualTo("REMINDER");
        assertThat(request.variables()).containsEntry("token", "12");
    }

    @Test
    void calledEventMapsToCalledTemplate() throws Exception {
        NotifyRequest request = consumer.toNotifyRequest(objectMapper.readTree("""
                {"eventType":"QUEUE_ITEM_CALLED","tenantId":"t1","patientCpid":"CPID-001","tokenNumber":12}
                """));

        assertThat(request).isNotNull();
        assertThat(request.templateKey()).isEqualTo("QUEUE_CITIZEN_CALLED");
    }

    @Test
    void escalatedEventNeverLeaksTheStaffFacingReason() throws Exception {
        NotifyRequest request = consumer.toNotifyRequest(objectMapper.readTree("""
                {"eventType":"QUEUE_ITEM_ESCALATED","tenantId":"t1","patientCpid":"CPID-001",
                 "tokenNumber":12,"reason":"suspected sepsis","escalatedBy":"nurse-9"}
                """));

        assertThat(request).isNotNull();
        assertThat(request.templateKey()).isEqualTo("QUEUE_CITIZEN_PRIORITISED");
        assertThat(request.variables()).doesNotContainKey("reason");
        assertThat(request.variables().values()).noneMatch(v -> v.contains("sepsis"));
    }

    @Test
    void transferredEventUsesTheNewTokenNumber() throws Exception {
        NotifyRequest request = consumer.toNotifyRequest(objectMapper.readTree("""
                {"eventType":"QUEUE_ITEM_TRANSFERRED","tenantId":"t1","patientCpid":"CPID-001",
                 "newTokenNumber":4}
                """));

        assertThat(request).isNotNull();
        assertThat(request.templateKey()).isEqualTo("QUEUE_CITIZEN_TRANSFERRED");
        assertThat(request.variables()).containsEntry("token", "4");
    }

    @Test
    void noShowStatusChangeMapsToNoShowTemplate() throws Exception {
        NotifyRequest request = consumer.toNotifyRequest(objectMapper.readTree("""
                {"eventType":"QUEUE_ITEM_UPDATED","tenantId":"t1","patientCpid":"CPID-001",
                 "tokenNumber":12,"previousStatus":"CALLED","newStatus":"NO_SHOW"}
                """));

        assertThat(request).isNotNull();
        assertThat(request.templateKey()).isEqualTo("QUEUE_CITIZEN_NO_SHOW");
    }

    @Test
    void internalStatusChangesProduceNoPatientMessage() throws Exception {
        assertThat(consumer.toNotifyRequest(objectMapper.readTree("""
                {"eventType":"QUEUE_ITEM_UPDATED","tenantId":"t1","patientCpid":"CPID-001",
                 "newStatus":"IN_TRIAGE"}
                """))).isNull();
        assertThat(consumer.toNotifyRequest(objectMapper.readTree("""
                {"eventType":"QUEUE_ITEM_UPDATED","tenantId":"t1","patientCpid":"CPID-001",
                 "newStatus":"IN_SERVICE"}
                """))).isNull();
    }

    @Test
    void eventsWithoutCpidOrTypeAreSkipped() throws Exception {
        assertThat(consumer.toNotifyRequest(objectMapper.readTree(
                "{\"eventType\":\"QUEUE_ITEM_CALLED\",\"tenantId\":\"t1\"}"))).isNull();
        assertThat(consumer.toNotifyRequest(objectMapper.readTree(
                "{\"patientCpid\":\"CPID-001\"}"))).isNull();
    }

    // ── Full onQueueItemEvent listener path ──

    @Test
    void onQueueItemEvent_enqueuesWithRequestContextCarryingTheEventTenantId() {
        consumer.onQueueItemEvent(record("""
                {"eventType":"QUEUE_ITEM_CALLED","tenantId":"tenant-away-pod",
                 "patientCpid":"CPID-001","tokenNumber":12}
                """));

        ArgumentCaptor<NotifyRequest> requestCaptor = ArgumentCaptor.forClass(NotifyRequest.class);
        ArgumentCaptor<RequestContext> ctxCaptor = ArgumentCaptor.forClass(RequestContext.class);
        verify(notifyService).enqueue(requestCaptor.capture(), ctxCaptor.capture());

        // Tenant isolation: the notification is written under the EVENT's
        // tenant, never a consumer-local default.
        assertThat(ctxCaptor.getValue().tenantId()).isEqualTo("tenant-away-pod");
        assertThat(requestCaptor.getValue().templateKey()).isEqualTo("QUEUE_CITIZEN_CALLED");
        assertThat(requestCaptor.getValue().recipient()).isEqualTo("CPID-001");
    }

    @Test
    void onQueueItemEvent_skipsEventsWithMissingTenantId() {
        consumer.onQueueItemEvent(record("""
                {"eventType":"QUEUE_ITEM_CALLED","patientCpid":"CPID-001","tokenNumber":12}
                """));
        consumer.onQueueItemEvent(record("""
                {"eventType":"QUEUE_ITEM_CALLED","tenantId":"","patientCpid":"CPID-001"}
                """));

        verify(notifyService, never()).enqueue(any(), any());
    }

    @Test
    void onQueueItemEvent_neverThrowsOnMalformedPayload() {
        // At-least-once Kafka delivery: a poison message must be swallowed,
        // not re-thrown into the listener container.
        consumer.onQueueItemEvent(record("this is not json"));

        verify(notifyService, never()).enqueue(any(), any());
    }
}
