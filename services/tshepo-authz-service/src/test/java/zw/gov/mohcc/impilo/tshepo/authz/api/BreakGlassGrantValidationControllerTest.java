package zw.gov.mohcc.impilo.tshepo.authz.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassGrantValidationRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassGrantValidationResponse;
import zw.gov.mohcc.impilo.tshepo.authz.service.BreakGlassGrantValidationService;

import java.lang.reflect.RecordComponent;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The endpoint answers "do <b>I</b> hold a live break-glass grant" — never "does <b>they</b>".
 *
 * <p>It previously took the actor from the request body, which made it an oracle: any authenticated
 * caller could ask whether a named clinician held a live grant. These tests pin the actor to the
 * bearer's subject so that cannot come back.</p>
 */
@ExtendWith(MockitoExtension.class)
class BreakGlassGrantValidationControllerTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock BreakGlassGrantValidationService service;

    private BreakGlassGrantValidationRequest request() {
        return new BreakGlassGrantValidationRequest(TENANT, null, "O_NEG_EMERGENCY_RELEASE");
    }

    private static Jwt jwtFor(String subject) {
        return Jwt.withTokenValue("t").header("alg", "none").claim("sub", subject).build();
    }

    @Test
    void theActorIsTheBearersSubjectNotAnythingTheCallerSupplied() {
        when(service.validate(any(), any()))
                .thenReturn(new BreakGlassGrantValidationResponse("NO_GRANT", null, null, null, null));

        new BreakGlassGrantValidationController(service).validate(jwtFor("clinician-1"), request());

        // The whole point: the service is asked about the TOKEN's subject.
        verify(service).validate(eq("clinician-1"), any());
    }

    @Test
    void theRequestBodyCannotNameAnActor() {
        // Structural, not stylistic. If someone re-adds an actor field, this fails — which is the
        // control. A body that can name an actor is an oracle over other people's grants, whatever
        // the controller happens to do with it today.
        for (RecordComponent c : BreakGlassGrantValidationRequest.class.getRecordComponents()) {
            String n = c.getName().toLowerCase();
            assertFalse(n.contains("actor") || n.equals("subject") || n.contains("healthid"),
                    "the validation request gained a caller-supplied actor: " + c.getName());
        }
    }

    @Test
    void aTokenWithNoSubjectIsRefusedRatherThanMatchedAgainstNull() {
        // A null actor would match no grant, read as NO_GRANT, and refuse — an authentication
        // problem arriving disguised as a clinical verdict.
        ResponseEntity<BreakGlassGrantValidationResponse> res =
                new BreakGlassGrantValidationController(service).validate(jwtFor(null), request());

        assertEquals(401, res.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void anAbsentTokenIsRefused() {
        ResponseEntity<BreakGlassGrantValidationResponse> res =
                new BreakGlassGrantValidationController(service).validate(null, request());

        assertEquals(401, res.getStatusCode().value());
        verifyNoInteractions(service);
    }
}
