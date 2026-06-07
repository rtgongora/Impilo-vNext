package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingControllerTest {

    @Test
    void createBooking_delegatesToBookingService() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode created = mapper.createObjectNode();
        created.put("id", "b1000000-0000-0000-0000-000000000001");
        created.put("bookingStatus", "REQUESTED");
        when(bookingClient.createBooking(any())).thenReturn(created);

        BookingController controller = new BookingController(bookingClient);

        var response = controller.createBooking(
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                "provider-actor-1",
                new BookingController.CreateBookingBody(
                        "patient-1",
                        "OPD",
                        null,
                        null,
                        "f1000000-0000-0000-0000-000000000001",
                        "provider-1",
                        null,
                        null,
                        "2026-06-10T09:00:00Z",
                        "2026-06-10T09:30:00Z",
                        "PHYSICAL",
                        "Follow-up visit",
                        null
                )
        );

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(bookingClient).createBooking(any());
    }

    @Test
    void approveBooking_delegatesToBookingService() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        UUID bookingId = UUID.fromString("b1000000-0000-0000-0000-000000000001");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode approved = mapper.createObjectNode();
        approved.put("id", bookingId.toString());
        approved.put("bookingStatus", "APPROVED");
        when(bookingClient.approveBooking(bookingId.toString())).thenReturn(approved);

        BookingController controller = new BookingController(bookingClient);

        var response = controller.approveBooking(
                bookingId,
                "tenant-1",
                "req-1",
                "corr-1"
        );

        assertEquals(200, response.getStatusCode().value());
        verify(bookingClient).approveBooking(bookingId.toString());
    }

    @Test
    void convertToAppointment_delegatesToBookingService() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        UUID bookingId = UUID.fromString("b1000000-0000-0000-0000-000000000001");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode appointment = mapper.createObjectNode();
        appointment.put("id", "a1000000-0000-0000-0000-000000000099");
        appointment.put("bookingId", bookingId.toString());
        when(bookingClient.convertBookingToAppointment(bookingId.toString())).thenReturn(appointment);

        BookingController controller = new BookingController(bookingClient);

        var response = controller.convertToAppointment(
                bookingId,
                "tenant-1",
                "req-1",
                "corr-1"
        );

        assertEquals(201, response.getStatusCode().value());
        verify(bookingClient).convertBookingToAppointment(bookingId.toString());
    }

    @Test
    void listBookings_delegatesWithFacilityFilter() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode rows = mapper.createArrayNode();
        ObjectNode row = rows.addObject();
        row.put("id", "b1");
        row.put("bookingStatus", "REQUESTED");
        when(bookingClient.listBookings(
                isNull(), eq("f1000000-0000-0000-0000-000000000001"), isNull(), eq("REQUESTED"), eq(0), eq(20)))
                .thenReturn(rows);

        BookingController controller = new BookingController(bookingClient);

        var response = controller.listBookings(
                "tenant-1",
                "req-1",
                "corr-1",
                null,
                "f1000000-0000-0000-0000-000000000001",
                null,
                "REQUESTED",
                0,
                20
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(bookingClient).listBookings(
                isNull(), eq("f1000000-0000-0000-0000-000000000001"), isNull(), eq("REQUESTED"), eq(0), eq(20));
    }
}
