package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RB-6 regulator user administration: the mutating acts are LOA3-gated and fail-closed.
 *
 * <p>Administering who holds authority at a statutory regulator must not proceed on an unproven
 * identity, and an identity-assurance outage must refuse rather than open the door — the same
 * property the facility/provider/bootstrap gates hold, verified here for the roster's mutations.</p>
 */
class RegulatorUserAdminControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IdentityAssuranceServiceClient assuranceAt(String level) {
        IdentityAssuranceServiceClient client = mock(IdentityAssuranceServiceClient.class);
        when(client.getAssuranceStatus()).thenReturn(
                level == null ? null : MAPPER.createObjectNode().put("currentLevel", level));
        return client;
    }

    private IdentityAssuranceServiceClient assuranceFailing() {
        IdentityAssuranceServiceClient client = mock(IdentityAssuranceServiceClient.class);
        when(client.getAssuranceStatus()).thenThrow(new RuntimeException("assurance down"));
        return client;
    }

    @Test
    void invite_belowLoa3_isRefusedAndNeverReachesOrgRegistry() {
        OrganizationRegistryServiceClient org = mock(OrganizationRegistryServiceClient.class);
        RegulatorUserAdminController controller = new RegulatorUserAdminController(org, assuranceAt("LOA2"));

        ResponseEntity<Map<String, Object>> resp = controller.invite(
                "req", "corr", "actor-1", "org-1", Map.of("roleCode", "REGISTRATION_OFFICER"));

        assertEquals(403, resp.getStatusCode().value());
        assertEquals("IDENTITY_PROOFING_REQUIRED",
                ((Map<?, ?>) resp.getBody().get("error")).get("code"));
        verify(org, never()).createInvitation(anyString(), any());
    }

    @Test
    void invite_atLoa3_reachesTheInvitationRail() {
        OrganizationRegistryServiceClient org = mock(OrganizationRegistryServiceClient.class);
        when(org.createInvitation(anyString(), any()))
                .thenReturn(MAPPER.createObjectNode().set("data", MAPPER.createObjectNode().put("id", "inv-1")));
        RegulatorUserAdminController controller = new RegulatorUserAdminController(org, assuranceAt("LOA3"));

        ResponseEntity<Map<String, Object>> resp = controller.invite(
                "req", "corr", "actor-1", "org-1", Map.of("roleCode", "REGISTRATION_OFFICER"));

        assertEquals(201, resp.getStatusCode().value());
        verify(org).createInvitation(anyString(), any());
    }

    @Test
    void verify_whenAssuranceServiceIsDown_failsClosed() {
        OrganizationRegistryServiceClient org = mock(OrganizationRegistryServiceClient.class);
        RegulatorUserAdminController controller = new RegulatorUserAdminController(org, assuranceFailing());

        ResponseEntity<Map<String, Object>> resp = controller.verify("req", "corr", "appt-1", null);

        assertEquals(403, resp.getStatusCode().value(),
                "an assurance outage must refuse, never open the door onto a regulator's roster");
        verify(org, never()).verifyAppointment(anyString(), any());
    }

    @Test
    void end_surfacesTheSuccessionGuardConflict_notAGeneric502() {
        // The org-registry client wraps HTTP errors in an IllegalStateException; the last-administrator
        // 409 must reach the operator as a 409 with its reason, not be collapsed into an opaque 502.
        OrganizationRegistryServiceClient org = mock(OrganizationRegistryServiceClient.class);
        HttpClientErrorException conflict = HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY,
                "{\"error\":{\"code\":\"LAST_ADMINISTRATOR\",\"message\":\"Appoint a replacement first\"}}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(org.endAppointment(anyString(), any()))
                .thenThrow(new IllegalStateException("organization-registry POST failed", conflict));
        RegulatorUserAdminController controller = new RegulatorUserAdminController(org, assuranceAt("LOA3"));

        ResponseEntity<Map<String, Object>> resp = controller.end("req", "corr", "appt-1", null);

        assertEquals(409, resp.getStatusCode().value());
        assertEquals("LAST_ADMINISTRATOR",
                ((Map<?, ?>) resp.getBody().get("error")).get("code"));
    }

    @Test
    void roster_isUngated_andReturnsTheAppointments() {
        OrganizationRegistryServiceClient org = mock(OrganizationRegistryServiceClient.class);
        when(org.listAppointmentsForOrganization("org-1"))
                .thenReturn(MAPPER.createArrayNode().add(MAPPER.createObjectNode().put("roleCode", "REGISTRAR")));
        // Even with no assurance, a read of who administers a regulator is allowed.
        RegulatorUserAdminController controller = new RegulatorUserAdminController(org, assuranceAt(null));

        ResponseEntity<Map<String, Object>> resp = controller.roster("req", "corr", "org-1");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(org).listAppointmentsForOrganization("org-1");
    }
}
