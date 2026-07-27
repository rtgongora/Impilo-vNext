package zw.gov.mohcc.impilo.experience.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.facility.FacilityNameResolver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentCommsWorkflowServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private NotificationServiceClient notify;
    private AppointmentCommsWorkflowService service;

    @BeforeEach
    void setUp() {
        notify = mock(NotificationServiceClient.class);
        FacilityNameResolver facilityNames = new FacilityNameResolver(stubTusoClient());
        AppointmentProviderRecipientResolver recipients =
                new AppointmentProviderRecipientResolver(stubVashandiClient());
        TshepoAuditServiceClient audit = stubAuditClient();
        AppointmentReminderReceiptStore reminders = new AppointmentReminderReceiptStore(mock(org.springframework.data.redis.core.StringRedisTemplate.class));
        service = new AppointmentCommsWorkflowService(
                notify, facilityNames, recipients, audit, reminders, "Africa/Harare");
    }

    /**
     * Stub audit client that captures ingested events in-memory and replays them through
     * {@code queryEvents} so read-after-write history assertions hold without a live audit sovereign.
     */
    private TshepoAuditServiceClient stubAuditClient() {
        TshepoAuditServiceClient audit = mock(TshepoAuditServiceClient.class);
        List<ObjectNode> events = new ArrayList<>();
        doAnswer(inv -> {
            java.util.Map<String, Object> body = inv.getArgument(0);
            events.add(mapper.valueToTree(body));
            return null;
        }).when(audit).ingestAuditEvent(any());
        when(audit.queryEvents(isNull(), anyString(), anyString(), isNull(), isNull(), anyInt(), anyInt()))
                .thenAnswer(inv -> filterEvents(events, inv.getArgument(1), inv.getArgument(2)));
        when(audit.queryEvents(isNull(), isNull(), anyString(), isNull(), isNull(), anyInt(), anyInt()))
                .thenAnswer(inv -> filterEvents(events, null, inv.getArgument(2)));
        return audit;
    }

    private JsonNode filterEvents(List<ObjectNode> events, String subjectRef, String eventType) {
        ObjectNode data = mapper.createObjectNode();
        ArrayNode items = data.putArray("items");
        for (ObjectNode ev : events) {
            if (eventType != null && !eventType.equals(ev.path("eventType").asText())) {
                continue;
            }
            if (subjectRef != null && !subjectRef.equals(ev.path("subjectRef").asText())) {
                continue;
            }
            items.add(ev);
        }
        return data;
    }

    @Test
    void onBookingCreated_notifiesCitizenAndProvider() {
        ObjectNode booking = mapper.createObjectNode();
        booking.put("id", "b1");
        booking.put("bookingNumber", "BK-100");
        booking.put("clientId", "cpid-1");
        booking.put("facilityId", 42);
        booking.put("bookingType", "GENERAL");
        booking.put("preferredStartAt", Instant.parse("2026-06-10T09:00:00Z").toString());

        service.onBookingCreated(booking);

        verify(notify, atLeastOnce()).sendNotification(argThat(body ->
                "cpid-1".equals(body.get("inboxRecipient"))));
        verify(notify, atLeastOnce()).sendNotification(argThat(body -> {
            Object inbox = body.get("inboxRecipient");
            return inbox != null && inbox.toString().startsWith("facility-scheduling:");
        }));
    }

    @Test
    void onBookingRejected_usesRejectedTemplate() {
        ObjectNode booking = mapper.createObjectNode();
        booking.put("clientId", "cpid-2");
        booking.put("facilityId", "99");
        booking.put("preferredStartAt", Instant.parse("2026-06-10T09:00:00Z").toString());

        service.onBookingRejected(booking, "Slot unavailable");

        verify(notify, atLeastOnce()).sendNotification(argThat(body ->
                "APPOINTMENT_CITIZEN_REJECTED".equals(body.get("templateKey"))));
    }

    @Test
    void onAppointmentCancelledByStaff_usesStaffProviderTemplate() {
        ObjectNode appt = mapper.createObjectNode();
        appt.put("id", "a1");
        appt.put("patientCpid", "cpid-9");
        appt.put("providerId", "prov-1");
        appt.put("facilityId", "42");
        appt.put("scheduledAt", Instant.parse("2026-06-11T10:00:00Z").toString());

        service.onAppointmentCancelled(appt, "Clinic closed", AppointmentCommsWorkflowService.INITIATOR_STAFF);

        verify(notify, atLeastOnce()).sendNotification(argThat(body ->
                "APPOINTMENT_PROVIDER_CANCELLED_BY_STAFF".equals(body.get("templateKey"))));
    }

    @Test
    void sendProviderToCitizenMessage_persistsThread() {
        ObjectNode appt = mapper.createObjectNode();
        appt.put("id", "a1");
        appt.put("patientCpid", "cpid-9");
        appt.put("providerName", "Dr Ncube");
        appt.put("scheduledAt", Instant.parse("2026-06-11T10:00:00Z").toString());

        service.sendProviderToCitizenMessage(appt, "Please arrive 15 minutes early", "provider-1");

        verify(notify, atLeastOnce()).sendNotification(argThat(body ->
                "APPOINTMENT_CITIZEN_MESSAGE".equals(body.get("templateKey"))
                        && "cpid-9".equals(body.get("inboxRecipient"))));
        assertEquals(1, service.listMessages("a1").size());
        assertTrue(service.listMessages("a1").get(0).get("message").toString().contains("15 minutes"));
    }

    private TusoServiceClient stubTusoClient() {
        TusoServiceClient client = mock(TusoServiceClient.class);
        when(client.getFacility(42L)).thenReturn(mapper.createObjectNode().put("name", "Harare Central Hospital"));
        return client;
    }

    /**
     * On-call is rostered in vashandi, not tuso. The scheduling lane repointed the resolver
     * at VashandiServiceClient but this test kept constructing it with a Tuso stub whose
     * listOnCall no longer exists, so the test module did not compile. Tuso still serves the
     * facility name; vashandi serves on-call.
     */
    private VashandiServiceClient stubVashandiClient() {
        VashandiServiceClient client = mock(VashandiServiceClient.class);
        when(client.listOnCall(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(mapper.createArrayNode());
        return client;
    }
}
