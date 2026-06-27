package zw.gov.mohcc.impilo.channels.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.channels.api.dto.NotifyOnlyResult;
import zw.gov.mohcc.impilo.companion.context.RequestContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotifyOnlyService} — content-free external notification with honest
 * configured/not-configured status.
 */
@ExtendWith(MockitoExtension.class)
class NotifyOnlyServiceTest {

    @Mock private OutboxService outboxService;

    private static final String TEMPLATE = "You have a secure health notification. Please log in to Impilo to view it.";

    private RequestContext ctx() {
        return RequestContext.of("moh-zw", "national", "req-1", "corr-1", null, null, null);
    }

    @Test
    @DisplayName("not configured: status QUEUED, message is the fixed template, event tagged notify_only")
    void queuedWhenNotConfigured() {
        NotifyOnlyService service = new NotifyOnlyService(outboxService, false, TEMPLATE);

        NotifyOnlyResult result = service.send(ctx(), "SMS", "+263771234567", "notif-1", "idem-1");

        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.message()).isEqualTo(TEMPLATE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxService).persistEvent(any(), eq("impilo.channels.notify_only.outbound.v1"),
                any(), any(), any(), any(), any(), payload.capture());
        assertThat(payload.getValue()).containsEntry("notify_only", true)
                .containsEntry("message", TEMPLATE)
                .containsEntry("status", "QUEUED");
    }

    @Test
    @DisplayName("configured: status DISPATCHED")
    void dispatchedWhenConfigured() {
        NotifyOnlyService service = new NotifyOnlyService(outboxService, true, TEMPLATE);

        NotifyOnlyResult result = service.send(ctx(), "SMS", "+263771234567", "notif-2", "idem-2");

        assertThat(result.status()).isEqualTo("DISPATCHED");
    }

    @Test
    @DisplayName("the API never carries caller content — only the template is emitted")
    void carriesNoCallerContent() {
        NotifyOnlyService service = new NotifyOnlyService(outboxService, false, TEMPLATE);

        NotifyOnlyResult result = service.send(ctx(), "EMAIL", "patient@example.com", "ACC-2026-AB-1", "idem-3");

        // Only the opaque reference is echoed; the body is always the template (no clinical detail).
        assertThat(result.message()).isEqualTo(TEMPLATE);
        assertThat(result.reference()).isEqualTo("ACC-2026-AB-1");
    }
}
