package zw.gov.mohcc.impilo.experience.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.facility.FacilityNameResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The cursor invariant: the poller must never advance past an event it did not actually handle.
 *
 * <p>Advancing past an unclaimed event is a permanent drop — the outbox is read forward-only, so a
 * skipped event is never re-offered and its appointment comms are simply never sent.
 */
class BookingOutboxCommsPollerTest {

    private static final String CURSOR_KEY = "impilo:booking:outbox:comms:cursor";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("dedup unavailable: the cursor is held, so the event is re-offered not dropped")
    void holdsCursorWhenClaimUnavailable() {
        FakeRedis redis = FakeRedis.shared();
        // Cursor reads/writes work; claims do not. See FakeRedis#template(boolean) for why the halves
        // are split: with both broken the cursor could not move anyway and the test would prove nothing.
        StringRedisTemplate template = redis.template(false);

        RecordingWorkflow workflow = new RecordingWorkflow(template);
        BookingOutboxCommsPoller poller =
                new BookingOutboxCommsPoller(stubOutbox(101L, 102L), workflow.service(), template, true);

        poller.poll();

        assertEquals(List.of(), workflow.sends, "nothing may be sent while claims are impossible");
        assertNull(template.opsForValue().get(CURSOR_KEY),
                "the cursor must not advance past an event that was never handled");
    }

    @Test
    @DisplayName("dedup healthy: events are handled and the cursor advances")
    void advancesCursorWhenClaimsSucceed() {
        FakeRedis redis = FakeRedis.shared();
        StringRedisTemplate template = redis.template();

        RecordingWorkflow workflow = new RecordingWorkflow(template);
        BookingOutboxCommsPoller poller =
                new BookingOutboxCommsPoller(stubOutbox(101L, 102L), workflow.service(), template, true);

        poller.poll();

        assertTrue(workflow.sends.size() >= 2, "both events must reach the send path; saw " + workflow.sends);
        assertEquals("102", template.opsForValue().get(CURSOR_KEY),
                "the cursor advances to the last handled event");
    }

    @Test
    @DisplayName("a peer replica's claim settles the event, so the cursor still advances")
    void advancesCursorWhenPeerAlreadyClaimed() {
        FakeRedis redis = FakeRedis.shared();
        StringRedisTemplate template = redis.template();

        // The peer replica claims both events first — this replica must neither re-send nor stall.
        AppointmentReminderReceiptStore peer = new AppointmentReminderReceiptStore(template);
        assertTrue(peer.tryClaim("booking-outbox:booking.created:101"));
        assertTrue(peer.tryClaim("booking-outbox:booking.created:102"));

        RecordingWorkflow workflow = new RecordingWorkflow(template);
        BookingOutboxCommsPoller poller =
                new BookingOutboxCommsPoller(stubOutbox(101L, 102L), workflow.service(), template, true);

        poller.poll();

        assertEquals(List.of(), workflow.sends, "this replica must not duplicate the peer's work");
        assertEquals("102", template.opsForValue().get(CURSOR_KEY),
                "work the peer provably owns is settled — holding the cursor here would loop forever");
    }

    /**
     * The real workflow service with only its outbound edge stubbed, so the poller is driven by the
     * production {@code processOutboxEvent} contract rather than a test double of it.
     */
    private static final class RecordingWorkflow {
        private final AppointmentCommsWorkflowService service;
        private final List<String> sends = new ArrayList<>();

        RecordingWorkflow(StringRedisTemplate template) {
            NotificationServiceClient notify = mock(NotificationServiceClient.class);
            when(notify.sendNotification(any())).thenAnswer(inv -> {
                sends.add(String.valueOf(inv.<Map<String, Object>>getArgument(0).get("templateKey")));
                return null;
            });
            this.service = new AppointmentCommsWorkflowService(
                    notify,
                    mock(FacilityNameResolver.class),
                    mock(AppointmentProviderRecipientResolver.class),
                    mock(TshepoAuditServiceClient.class),
                    new AppointmentReminderReceiptStore(template),
                    "Africa/Harare");
        }

        AppointmentCommsWorkflowService service() {
            return service;
        }
    }

    private BookingServiceClient stubOutbox(long... eventIds) {
        BookingServiceClient booking = mock(BookingServiceClient.class);
        ArrayNode rows = mapper.createArrayNode();
        for (long id : eventIds) {
            ObjectNode row = rows.addObject();
            row.put("id", id);
            row.put("eventType", "booking.created");
            ObjectNode payload = row.putObject("payload");
            payload.put("bookingId", "bk-" + id);
            payload.put("patientCpid", "cpid-" + id);
        }
        when(booking.listOutboxEvents(anyLong(), anyInt())).thenReturn(rows);
        return booking;
    }
}
