package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.controller.mobile.citizen.CitizenBookingController;
import zw.gov.mohcc.impilo.experience.scheduling.AppointmentCommsWorkflowService;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CitizenBookingControllerTest {

    @Test
    void list_delegatesToBookingClientForActor() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode items = mapper.createArrayNode();
        items.add(mapper.createObjectNode().put("id", "bk-1").put("bookingStatus", "REQUESTED"));
        when(bookingClient.listBookings(eq("actor-citizen-1"), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(items);

        CitizenBookingController controller = new CitizenBookingController(bookingClient, mock(AppointmentCommsWorkflowService.class));
        var response = controller.list("tenant", "req-1", "corr-1", "actor-citizen-1", null, 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(bookingClient).listBookings(eq("actor-citizen-1"), isNull(), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void create_delegatesToBookingClient() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode created = mapper.createObjectNode().put("id", "bk-new").put("bookingStatus", "REQUESTED");
        when(bookingClient.createCitizenBooking(eq("actor-citizen-1"), any())).thenReturn(created);

        CitizenBookingController controller = new CitizenBookingController(bookingClient, mock(AppointmentCommsWorkflowService.class));
        var body = new CitizenBookingController.CreateBookingBody("fac-1", "GENERAL", "2026-06-10", "Check-up");
        var response = controller.create("tenant", "pod", "req-1", "corr-1", "actor-citizen-1", body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(bookingClient).createCitizenBooking(eq("actor-citizen-1"), any());
    }

    @Test
    void cancel_delegatesToBookingClient() {
        BookingServiceClient bookingClient = mock(BookingServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode cancelled = mapper.createObjectNode().put("id", "bk-1").put("bookingStatus", "CANCELLED");
        when(bookingClient.cancelBooking(anyString(), anyString())).thenReturn(cancelled);

        CitizenBookingController controller = new CitizenBookingController(bookingClient, mock(AppointmentCommsWorkflowService.class));
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        var response = controller.cancel(id, "req-1", "corr-1", Map.of("reason", "Changed plans"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(bookingClient).cancelBooking(eq(id.toString()), eq("Changed plans"));
    }
}
