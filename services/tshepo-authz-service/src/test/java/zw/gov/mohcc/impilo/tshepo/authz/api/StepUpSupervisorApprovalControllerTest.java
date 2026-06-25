package zw.gov.mohcc.impilo.tshepo.authz.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.service.StepUpService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * G058: the SUPERVISOR_APPROVAL step-up flow now has a controller endpoint. The supervisor's
 * identity + authority come from the authenticated principal (server-side), and the path is
 * fail-closed for callers lacking a configured supervisor role.
 */
class StepUpSupervisorApprovalControllerTest {

    private final StepUpService service = mock(StepUpService.class);
    private final StepUpController controller = new StepUpController(service, new AuthzProperties());

    private static Jwt jwtWithRoles(String... roles) {
        return Jwt.withTokenValue("t").header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
    }

    @Test
    void supervisorRole_recordsApprovalAsAuthorised_204() {
        UUID challengeId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ResponseEntity<Void> resp = controller.recordSupervisorApproval(
                challengeId, jwtWithRoles("SUPERVISOR"), tenantId, "supervisor-1");

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(service).recordSupervisorApproval(eq(challengeId), eq(tenantId), eq("supervisor-1"), eq(true));
    }

    @Test
    void nonSupervisorRole_isPassedAsUnauthorised_andFailsClosed_403() {
        UUID challengeId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        // The service enforces dual control: unauthorised supervisor -> SecurityException.
        doThrow(new SecurityException("Supervisor approval requires an authorised supervisor principal"))
                .when(service).recordSupervisorApproval(challengeId, tenantId, "nurse-1", false);

        ResponseEntity<Void> resp = controller.recordSupervisorApproval(
                challengeId, jwtWithRoles("NURSE"), tenantId, "nurse-1");

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(service).recordSupervisorApproval(eq(challengeId), eq(tenantId), eq("nurse-1"), eq(false));
    }

    @Test
    void nullJwt_failsClosed_403() {
        UUID challengeId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        doThrow(new SecurityException("unauthorised"))
                .when(service).recordSupervisorApproval(challengeId, tenantId, "x", false);

        ResponseEntity<Void> resp = controller.recordSupervisorApproval(
                challengeId, null, tenantId, "x");

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void challengeNotPending_400() {
        UUID challengeId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Challenge not pending: " + challengeId))
                .when(service).recordSupervisorApproval(challengeId, tenantId, "supervisor-1", true);

        ResponseEntity<Void> resp = controller.recordSupervisorApproval(
                challengeId, jwtWithRoles("CLINICAL_SUPERVISOR"), tenantId, "supervisor-1");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }
}
