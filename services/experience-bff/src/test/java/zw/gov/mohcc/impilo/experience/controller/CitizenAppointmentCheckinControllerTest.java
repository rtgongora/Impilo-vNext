package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.service.AppointmentCheckInService;
import zw.gov.mohcc.impilo.experience.service.SubjectResolutionService;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CitizenAppointmentCheckinController (CJ11)")
class CitizenAppointmentCheckinControllerTest {

    private static final String TENANT = "t-1";
    private static final String ACTOR = "11111111-1111-1111-1111-111111111111";
    private static final String APPT = "22222222-2222-2222-2222-222222222222";
    private static final String REQ = "req-1";
    private static final String CORR = "corr-1";

    private final ObjectMapper mapper = new ObjectMapper();
    private BookingServiceClient booking;
    private TshepoIdentityServiceClient identity;
    private AppointmentCheckInService checkIn;
    private SubjectResolutionService subjects;
    private CitizenAppointmentCheckinController controller;

    @BeforeEach
    void setUp() {
        booking = mock(BookingServiceClient.class);
        identity = mock(TshepoIdentityServiceClient.class);
        checkIn = mock(AppointmentCheckInService.class);
        subjects = mock(SubjectResolutionService.class);
        controller = new CitizenAppointmentCheckinController(booking, identity, checkIn, subjects);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, Object>) r.getBody().get("data");
    }

    @Test
    @DisplayName("owner mints a scoped check-in token bound to the appointment")
    void mintsForOwner() throws Exception {
        when(booking.getAppointment(APPT)).thenReturn(mapper.readTree("{\"patient_id\":\"" + ACTOR + "\"}"));
        when(identity.issueScopedToken(anyMap()))
                .thenReturn(mapper.readTree("{\"token\":\"tok-xyz\",\"expiresAt\":\"2026-07-19T12:00:00Z\"}"));

        ResponseEntity<Map<String, Object>> r = controller.mintToken(APPT, TENANT, ACTOR, REQ, CORR);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).containsKeys("data", "meta"); // house envelope the shell requires
        assertThat(data(r).get("token")).isEqualTo("tok-xyz");
        assertThat(data(r).get("appointmentId")).isEqualTo(APPT);
        // The token scope must bind THIS appointment id, targeted at booking-service.
        verify(identity).issueScopedToken(argThat(m ->
                ("APPOINTMENT_CHECKIN:" + APPT).equals(m.get("scope"))
                        && "booking-service".equals(m.get("targetService"))
                        && ACTOR.equals(m.get("actorId"))));
    }

    @Test
    @DisplayName("a non-owner cannot mint a token (403), and none is issued")
    void nonOwnerForbidden() throws Exception {
        when(booking.getAppointment(APPT)).thenReturn(mapper.readTree("{\"patient_id\":\"someone-else\"}"));
        when(subjects.cpidForHealthId(ACTOR)).thenReturn("cpid-x"); // still no match on patient_cpid
        ResponseEntity<Map<String, Object>> r = controller.mintToken(APPT, TENANT, ACTOR, REQ, CORR);
        assertThat(r.getStatusCode().value()).isEqualTo(403);
        verify(identity, never()).issueScopedToken(anyMap());
    }

    @Test
    @DisplayName("ownership also matches via resolved CPID on patient_cpid")
    void ownerByCpid() throws Exception {
        when(booking.getAppointment(APPT)).thenReturn(mapper.readTree("{\"patient_cpid\":\"cpid-9\"}"));
        when(subjects.cpidForHealthId(ACTOR)).thenReturn("cpid-9");
        when(identity.issueScopedToken(anyMap())).thenReturn(mapper.readTree("{\"token\":\"t\"}"));
        ResponseEntity<Map<String, Object>> r = controller.mintToken(APPT, TENANT, ACTOR, REQ, CORR);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("a valid active token checks in the bound appointment")
    void checksInByToken() throws Exception {
        when(identity.introspectScopedToken(anyMap())).thenReturn(mapper.readTree(
                "{\"active\":true,\"scope\":\"APPOINTMENT_CHECKIN:" + APPT + "\",\"targetService\":\"booking-service\"}"));
        when(checkIn.checkIn(eq(UUID.fromString(APPT)), any(), any()))
                .thenReturn(ResponseEntity.ok(Map.of("status", "CHECKED_IN")));

        ResponseEntity<Map<String, Object>> r = controller.checkinByToken(
                new CitizenAppointmentCheckinController.CheckinByTokenRequest("tok"), "req", "corr");
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        verify(checkIn).checkIn(UUID.fromString(APPT), "req", "corr");
    }

    @Test
    @DisplayName("an inactive/expired token is rejected (401), no check-in")
    void inactiveTokenRejected() throws Exception {
        when(identity.introspectScopedToken(anyMap())).thenReturn(mapper.readTree("{\"active\":false}"));
        ResponseEntity<Map<String, Object>> r = controller.checkinByToken(
                new CitizenAppointmentCheckinController.CheckinByTokenRequest("tok"), "req", "corr");
        assertThat(r.getStatusCode().value()).isEqualTo(401);
        verify(checkIn, never()).checkIn(any(), any(), any());
    }

    @Test
    @DisplayName("a token minted for another purpose/service cannot be replayed here (400)")
    void wrongPurposeTokenRejected() throws Exception {
        when(identity.introspectScopedToken(anyMap())).thenReturn(mapper.readTree(
                "{\"active\":true,\"scope\":\"PATIENT_CONTEXT:x\",\"targetService\":\"pct-service\"}"));
        ResponseEntity<Map<String, Object>> r = controller.checkinByToken(
                new CitizenAppointmentCheckinController.CheckinByTokenRequest("tok"), "req", "corr");
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        verify(checkIn, never()).checkIn(any(), any(), any());
    }
}
