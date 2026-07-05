package zw.gov.mohcc.impilo.notification.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.service.NotifyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QueueLifecycleNotificationConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QueueLifecycleNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new QueueLifecycleNotificationConsumer(mock(NotifyService.class), objectMapper);
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
}
