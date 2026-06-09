package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SchedulingServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.scheduling.AppointmentCommsWorkflowService;
import zw.gov.mohcc.impilo.experience.service.AppointmentCheckInService;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulingControllerTest {

    @Test
    void createAppointment_delegatesToBookingServiceWithTypedContract() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode created = mapper.createObjectNode();
        created.put("id", "appt-99");
        created.put("status", "SCHEDULED");
        created.put("bookingId", "b1000000-0000-0000-0000-000000000001");
        when(bookingClient.createAppointment(any())).thenReturn(created);

        SchedulingController controller = newController(bookingClient);

        var response = controller.createAppointment(
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                "idem-1",
                new SchedulingController.CreateAppointmentRequest(
                        "patient-1",
                        "f1000000-0000-0000-0000-000000000001",
                        "provider-1",
                        "Dr. Moyo",
                        "OPD",
                        "2026-06-05T09:00:00Z",
                        "2026-06-05T09:30:00Z",
                        "Follow-up",
                        null,
                        "resource-1"
                )
        );

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(bookingClient).createAppointment(any());
    }

    @Test
    void checkInAppointment_returnsJourneyCorrelationMetaWhenPctSucceeds() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        PctServiceClient pctClient = mock(PctServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();

        UUID appointmentId = UUID.fromString("a1000000-0000-0000-0000-000000000099");
        UUID queueId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ObjectNode appointment = mapper.createObjectNode();
        appointment.put("patient_id", "patient-1");
        appointment.put("facility_id", "f1000000-0000-0000-0000-000000000001");
        appointment.put("bookingId", "b1000000-0000-0000-0000-000000000001");
        when(bookingClient.getAppointment(appointmentId.toString())).thenReturn(appointment);

        var queueArray = mapper.createArrayNode();
        var queueDef = queueArray.addObject();
        queueDef.put("queueType", "WALK_IN");
        queueDef.put("queueId", queueId.toString());
        when(pctClient.listQueues(any(UUID.class), any())).thenReturn(queueArray);

        ObjectNode checkedIn = mapper.createObjectNode();
        checkedIn.put("status", "CHECKED_IN");
        checkedIn.put("encounter_id", "9001");
        checkedIn.put("queue_token_id", "22222222-2222-2222-2222-222222222222");
        when(bookingClient.checkInAppointment(appointmentId.toString(), queueId.toString())).thenReturn(checkedIn);

        ObjectNode encounterNode = mapper.createObjectNode();
        encounterNode.put("id", 9001);
        encounterNode.put("journeyId", "journey-9001");
        when(pctClient.getEncounter(9001L)).thenReturn(encounterNode);

        SchedulingController controller = newController(bookingClient, pctClient);

        var response = controller.checkInAppointment(
                appointmentId,
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                null
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertEquals("9001", meta.get("encounter_id"));
        assertEquals("encounter-9001", meta.get("core_transaction_id"));
        assertEquals("journey-9001", meta.get("journey_id"));
        assertEquals("patient-1", meta.get("patient_id"));
        assertEquals(appointmentId.toString(), meta.get("appointment_id"));
        assertEquals("CHECKED_IN", meta.get("booking_status"));
        verify(bookingClient).checkInAppointment(appointmentId.toString(), queueId.toString());
        verify(pctClient, never()).startJourney(anyString(), any(UUID.class), any(), any());
    }

    @Test
    void checkInAppointment_failsBeforePctWhenBookingCheckInFails() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        PctServiceClient pctClient = mock(PctServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        UUID appointmentId = UUID.fromString("a1000000-0000-0000-0000-000000000099");
        ObjectNode appointment = mapper.createObjectNode();
        appointment.put("facility_id", "f1000000-0000-0000-0000-000000000001");
        when(bookingClient.getAppointment(appointmentId.toString())).thenReturn(appointment);
        when(bookingClient.checkInAppointment(anyString(), any())).thenReturn(null);

        SchedulingController controller = newController(bookingClient, pctClient);
        var response = controller.checkInAppointment(
                appointmentId, "tenant-1", "pod-1", "req-1", "corr-1", null);

        assertEquals(502, response.getStatusCode().value());
        verify(pctClient, never()).startJourney(anyString(), any(UUID.class), any(), any());
    }

    @Test
    void checkInAppointment_failsClosedWhenAppointmentMissing() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        UUID appointmentId = UUID.fromString("a1000000-0000-0000-0000-000000000099");
        when(bookingClient.getAppointment(appointmentId.toString())).thenReturn(null);

        SchedulingController controller = newController(bookingClient);

        var response = controller.checkInAppointment(
                appointmentId,
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                null
        );

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("APPOINTMENT_NOT_FOUND", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static SchedulingController newController(BookingServiceClient bookingClient) {
        PctServiceClient pctClient = mock(PctServiceClient.class);
        return newController(bookingClient, pctClient);
    }

    private static SchedulingController newController(BookingServiceClient bookingClient, PctServiceClient pctClient) {
        AppointmentCommsWorkflowService comms = mock(AppointmentCommsWorkflowService.class);
        return new SchedulingController(
                bookingClient,
                mock(TusoServiceClient.class),
                mock(SchedulingServiceClient.class),
                new AppointmentCheckInService(bookingClient, pctClient, comms),
                comms);
    }
}
