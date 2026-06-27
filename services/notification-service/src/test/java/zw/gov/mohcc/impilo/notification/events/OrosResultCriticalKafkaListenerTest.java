package zw.gov.mohcc.impilo.notification.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.service.NotifyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrosResultCriticalKafkaListener} — enqueues an urgent in-platform alert to
 * the requester from an enriched OROS critical-result event.
 */
@ExtendWith(MockitoExtension.class)
class OrosResultCriticalKafkaListenerTest {

    @Mock private NotifyService notifyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrosResultCriticalKafkaListener listener() {
        return new OrosResultCriticalKafkaListener(objectMapper, notifyService);
    }

    @Test
    @DisplayName("enqueues an IN_APP CRITICAL alert to the requester with order context variables")
    void enqueuesCriticalAlert() {
        String json = """
                {"tenantId":"t-1","requesterId":"dr-9","orderId":"ORD-1","resultId":"r-1",
                 "patientCpid":"CPID-1","accessionNumber":"ACC-2026-AB-1","criticalReason":"K+ 7.2",
                 "reportedBy":"radiologist-3"}
                """;

        listener().onCriticalResult(json);

        ArgumentCaptor<NotifyRequest> req = ArgumentCaptor.forClass(NotifyRequest.class);
        ArgumentCaptor<RequestContext> ctx = ArgumentCaptor.forClass(RequestContext.class);
        verify(notifyService).enqueue(req.capture(), ctx.capture());

        assertThat(req.getValue().channel()).isEqualTo("IN_APP");
        assertThat(req.getValue().recipient()).isEqualTo("dr-9");
        assertThat(req.getValue().inboxRecipient()).isEqualTo("dr-9");
        assertThat(req.getValue().messageKind()).isEqualTo("CRITICAL");
        assertThat(req.getValue().patientRef()).isEqualTo("CPID-1");
        assertThat(req.getValue().variables()).containsEntry("criticalReason", "K+ 7.2")
                .containsEntry("accessionNumber", "ACC-2026-AB-1");
        assertThat(ctx.getValue().tenantId()).isEqualTo("t-1");
    }

    @Test
    @DisplayName("skips events missing tenantId or requesterId")
    void skipsIncompleteEvents() {
        listener().onCriticalResult("{\"orderId\":\"ORD-1\"}");
        verify(notifyService, never()).enqueue(any(), any());
    }

    @Test
    @DisplayName("tolerates malformed JSON without throwing")
    void toleratesMalformedJson() {
        listener().onCriticalResult("not json");
        verify(notifyService, never()).enqueue(any(), any());
    }
}
