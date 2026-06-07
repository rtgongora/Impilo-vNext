package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.controller.mobile.citizen.CitizenAppointmentController;

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

        CitizenAppointmentController controller = new CitizenAppointmentController(bookingClient);

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

        CitizenAppointmentController controller = new CitizenAppointmentController(bookingClient);

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
        CitizenAppointmentController controller = new CitizenAppointmentController(bookingClient);

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
}
