package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.controller.mobile.citizen.CitizenAppointmentController;
import zw.gov.mohcc.impilo.experience.service.AppointmentCheckInService;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CitizenAppointmentControllerTest {

    @Test
    void create_delegatesToBookingCitizenBookingForActor() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode created = mapper.createObjectNode();
        created.put("id", "booking-citizen-1");
        created.put("bookingStatus", "REQUESTED");
        when(bookingClient.createCitizenBooking(eq("actor-citizen-1"), any())).thenReturn(created);

        CitizenAppointmentController controller = newController(bookingClient, mock(PctServiceClient.class));

        var response = controller.create(
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                "actor-citizen-1",
                new CitizenAppointmentController.RequestAppointmentBody(
                        "f1000000-0000-0000-0000-000000000001",
                        "GENERAL",
                        "2026-06-10T09:00:00Z",
                        "Annual check-up"
                )
        );

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(bookingClient).createCitizenBooking(eq("actor-citizen-1"), any());
    }

    @Test
    void list_delegatesToBookingCitizenAppointmentFeed() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode rows = mapper.createArrayNode();
        ObjectNode row = rows.addObject();
        row.put("id", "apt-1");
        row.put("status", "CONFIRMED");
        row.put("bookingId", "b1000000-0000-0000-0000-000000000001");
        when(bookingClient.listCitizenAppointments("actor-citizen-1", null, 0, 20)).thenReturn(rows);

        CitizenAppointmentController controller = newController(bookingClient, mock(PctServiceClient.class));

        var response = controller.list(
                "tenant-1",
                "req-1",
                "corr-1",
                "actor-citizen-1",
                null,
                0,
                20
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(bookingClient).listCitizenAppointments("actor-citizen-1", null, 0, 20);
    }

    @Test
    void cancel_delegatesToBookingCancel() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        UUID appointmentId = UUID.fromString("a1000000-0000-0000-0000-000000000099");
        CitizenAppointmentController controller = newController(bookingClient, mock(PctServiceClient.class));

        var response = controller.cancel(
                appointmentId,
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                Map.of("reason", "Cannot attend")
        );

        assertEquals(200, response.getStatusCode().value());
        verify(bookingClient).cancelAppointment(appointmentId.toString(), "Cannot attend");
    }

    @Test
    void checkIn_delegatesToSharedCheckInServiceWhenActorOwnsAppointment() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        PctServiceClient pctClient = mock(PctServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        UUID appointmentId = UUID.fromString("a1000000-0000-0000-0000-000000000099");
        UUID queueId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        ObjectNode appointment = mapper.createObjectNode();
        appointment.put("patient_id", "actor-citizen-1");
        appointment.put("facility_id", "f1000000-0000-0000-0000-000000000001");
        when(bookingClient.getAppointment(appointmentId.toString())).thenReturn(appointment);

        var queueArray = mapper.createArrayNode();
        queueArray.addObject().put("queueType", "WALK_IN").put("queueId", queueId.toString());
        when(pctClient.listQueues(any(UUID.class), any())).thenReturn(queueArray);

        ObjectNode checkedIn = mapper.createObjectNode();
        checkedIn.put("status", "CHECKED_IN");
        checkedIn.put("encounter_id", "9001");
        when(bookingClient.checkInAppointment(appointmentId.toString(), queueId.toString())).thenReturn(checkedIn);

        ObjectNode encounterNode = mapper.createObjectNode();
        encounterNode.put("journeyId", "journey-9001");
        when(pctClient.getEncounter(9001L)).thenReturn(encounterNode);

        CitizenAppointmentController controller = newController(bookingClient, pctClient);
        var response = controller.checkIn(
                appointmentId,
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                "actor-citizen-1");

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertEquals("encounter-9001", meta.get("core_transaction_id"));
    }

    private static CitizenAppointmentController newController(
            BookingServiceClient bookingClient,
            PctServiceClient pctClient) {
        return new CitizenAppointmentController(bookingClient, new AppointmentCheckInService(bookingClient, pctClient));
    }
}
