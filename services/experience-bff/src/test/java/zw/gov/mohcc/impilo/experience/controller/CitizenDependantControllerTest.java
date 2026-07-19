package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CitizenDependantController (CJ14)")
class CitizenDependantControllerTest {

    private static final String TENANT = "t-1";
    private static final String GUARDIAN = "guardian-1";
    private static final String CHILD_HID = "child-health-id";

    private final ObjectMapper mapper = new ObjectMapper();
    private VitoServiceClient vito;
    private MvumoServiceClient mvumo;
    private CitizenDependantController controller;

    @BeforeEach
    void setUp() {
        vito = mock(VitoServiceClient.class);
        mvumo = mock(MvumoServiceClient.class);
        controller = new CitizenDependantController(vito, mvumo);
    }

    private CitizenDependantController.AddDependantRequest child(String dob, List<String> scope) {
        return new CitizenDependantController.AddDependantRequest("Tinashe", "Moyo", dob, "M", scope);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<Map<String, Object>> r) {
        return r.getBody();
    }

    @Test
    @DisplayName("registers the child and records a guardian delegation expiring at 18")
    void addsMinorDependant() throws Exception {
        String dob = LocalDate.now().minusYears(5).toString(); // a 5-year-old
        when(vito.registerIdentity(anyMap())).thenReturn(mapper.readTree("{\"healthId\":\"" + CHILD_HID + "\"}"));
        when(mvumo.createDelegation(anyMap())).thenReturn(mapper.readTree("{\"id\":\"rel-1\",\"status\":\"GRANTED\"}"));

        ResponseEntity<Map<String, Object>> r = controller.addDependant(child(dob, null), TENANT, GUARDIAN);

        assertThat(r.getStatusCode().value()).isEqualTo(201);
        assertThat(body(r).get("status")).isEqualTo("CREATED");
        assertThat(body(r).get("dependantSubjectRef")).isEqualTo(CHILD_HID);

        String expectedExpiryYear = String.valueOf(LocalDate.now().minusYears(5).plusYears(18).getYear());
        verify(mvumo).createDelegation(argThat(m ->
                "GUARDIAN".equals(m.get("relationshipType"))
                        && "GUARDIANSHIP".equals(m.get("legalBasis"))
                        && CHILD_HID.equals(m.get("delegatorSubjectRef"))
                        && GUARDIAN.equals(m.get("delegateActorId"))
                        && m.get("expiresAt").toString().startsWith(expectedExpiryYear)
                        && ((List<?>) m.get("scope")).contains("*")));
    }

    @Test
    @DisplayName("a person at/over the age of majority is rejected (register/delegation never called)")
    void rejectsAdult() {
        String dob = LocalDate.now().minusYears(20).toString();
        ResponseEntity<Map<String, Object>> r = controller.addDependant(child(dob, null), TENANT, GUARDIAN);
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        verifyNoInteractions(vito);
        verifyNoInteractions(mvumo);
    }

    @Test
    @DisplayName("a custom scope is passed through instead of the full-guardianship default")
    void customScopePassedThrough() throws Exception {
        String dob = LocalDate.now().minusYears(3).toString();
        when(vito.registerIdentity(anyMap())).thenReturn(mapper.readTree("{\"healthId\":\"" + CHILD_HID + "\"}"));
        when(mvumo.createDelegation(anyMap())).thenReturn(mapper.readTree("{}"));

        controller.addDependant(child(dob, List.of("immunization", "appointment")), TENANT, GUARDIAN);
        verify(mvumo).createDelegation(argThat(m -> {
            List<?> scope = (List<?>) m.get("scope");
            return scope.contains("immunization") && !scope.contains("*");
        }));
    }

    @Test
    @DisplayName("missing guardian actor is rejected before any upstream call")
    void missingGuardianRejected() {
        String dob = LocalDate.now().minusYears(4).toString();
        ResponseEntity<Map<String, Object>> r = controller.addDependant(child(dob, null), TENANT, "  ");
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(vito);
    }

    @Test
    @DisplayName("missing required child fields are rejected")
    void missingFieldsRejected() {
        ResponseEntity<Map<String, Object>> r = controller.addDependant(
                new CitizenDependantController.AddDependantRequest("Tinashe", null, null, "M", null),
                TENANT, GUARDIAN);
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(vito);
    }
}
