package zw.gov.mohcc.impilo.learning.fundo.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class FundoNotificationDispatcherTest {

    @Mock private JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Object> pendingRow() {
        return Map.of(
                "id", UUID.randomUUID(),
                "tenant_id", UUID.fromString("00000000-0000-4000-8000-000000000001"),
                "subject_type", "PROVIDER",
                "subject_id", "PRV-1",
                "event_code", "learning.certificate.expired",
                "title", "Certificate expired",
                "message", "Renew it",
                "channel_preference", "IN_APP",
                "metadata_json", "{}");
    }

    @Test
    void stubProviderRecordsButNeverMarksSent() {
        FundoNotificationDispatcher dispatcher = new FundoNotificationDispatcher(
                jdbcTemplate, objectMapper,
                List.of(new StubLearningNotificationProvider()),
                true, "STUB", 100);
        when(jdbcTemplate.queryForList(any(String.class), anyInt()))
                .thenReturn(List.of(pendingRow()));

        int handled = dispatcher.drainOnce();

        assertThat(handled).isEqualTo(1);
        // status RECORDED and sent_at NULL — honest: intent acknowledged, not delivered.
        verify(jdbcTemplate).update(
                any(String.class), eq("RECORDED"), isNull(), any(), any(UUID.class));
    }

    @Test
    void realProviderMarksSentWhenDelivered() {
        LearningNotificationProvider fakeReal = new LearningNotificationProvider() {
            @Override public String key() { return "NOTIFICATION_SERVICE"; }
            @Override public DeliveryOutcome deliver(Intent intent) {
                return DeliveryOutcome.sent("delivered");
            }
        };
        FundoNotificationDispatcher dispatcher = new FundoNotificationDispatcher(
                jdbcTemplate, objectMapper,
                List.of(new StubLearningNotificationProvider(), fakeReal),
                true, "NOTIFICATION_SERVICE", 100);
        when(jdbcTemplate.queryForList(any(String.class), anyInt()))
                .thenReturn(List.of(pendingRow()));

        int handled = dispatcher.drainOnce();

        assertThat(handled).isEqualTo(1);
        verify(jdbcTemplate).update(
                any(String.class), eq("SENT"), any(java.time.OffsetDateTime.class), any(), any(UUID.class));
    }

    @Test
    void disabledDispatcherDoesNothing() {
        FundoNotificationDispatcher dispatcher = new FundoNotificationDispatcher(
                jdbcTemplate, objectMapper,
                List.of(new StubLearningNotificationProvider()),
                false, "STUB", 100);
        dispatcher.dispatchPending();
        verify(jdbcTemplate, never()).queryForList(any(String.class), anyInt());
    }
}
