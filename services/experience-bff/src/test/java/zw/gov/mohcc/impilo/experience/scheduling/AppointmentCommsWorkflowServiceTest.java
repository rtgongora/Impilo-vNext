package zw.gov.mohcc.impilo.experience.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.facility.FacilityNameResolver;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
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
                new AppointmentProviderRecipientResolver(stubTusoClient());
        AppointmentCommsHistoryStore history = new AppointmentCommsHistoryStore();
        AppointmentReminderReceiptStore reminders = new AppointmentReminderReceiptStore(mock(org.springframework.data.redis.core.StringRedisTemplate.class));
        service = new AppointmentCommsWorkflowService(
                notify, facilityNames, recipients, history, reminders, "Africa/Harare");
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
        when(client.listOnCall(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(mapper.createArrayNode());
        return client;
    }
}
