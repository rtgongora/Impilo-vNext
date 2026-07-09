package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.trustconsole.TrustConsoleService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Envelope + validation coverage for the Trust Console BFF routes, matching
 * {@link FacilityClaimControllerTest} conventions (pure Mockito, headers bind identity).
 */
@ExtendWith(MockitoExtension.class)
class TrustConsoleControllerTest {

    private static final String TENANT = "tenant-1";
    private static final String ACTOR = "11111111-2222-3333-4444-555555555555";
    private static final String REQ = "req-1";
    private static final String CORR = "corr-1";

    @Mock private TrustConsoleService trustConsoleService;

    private TrustConsoleController controller() {
        return new TrustConsoleController(trustConsoleService);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> resp) {
        assertNotNull(resp.getBody());
        return (Map<String, Object>) resp.getBody().get("data");
    }

    @Test
    void summary_wrapsAttributesInStandardEnvelope() {
        when(trustConsoleService.summary(TENANT, ACTOR, null, false))
                .thenReturn(Map.of("integrationStatus", "live"));

        ResponseEntity<Map<String, Object>> resp =
                controller().summary(TENANT, ACTOR, REQ, CORR, null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("trust-console", data(resp).get("type"));
        assertNotNull(resp.getBody().get("meta"));
        verify(trustConsoleService).summary(TENANT, ACTOR, null, false);
    }

    @Test
    void queue_unknownQueueIs400NotAThrow() {
        when(trustConsoleService.queue(eq(TENANT), eq(ACTOR), any(), anyBoolean(), eq("everything")))
                .thenThrow(new IllegalArgumentException("Unknown trust-console queue: everything"));

        ResponseEntity<Map<String, Object>> resp =
                controller().queue(TENANT, ACTOR, REQ, CORR, null, null, "everything");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void decide_requiresDecisionValue() {
        ResponseEntity<Map<String, Object>> resp = controller().decide(
                TENANT, ACTOR, REQ, CORR, null, null,
                "provider-access-requests", "PAR-1", Map.of("note", "missing decision"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verifyNoInteractions(trustConsoleService);
    }

    @Test
    void decide_forwardsQueueItemDecisionAndNote() {
        when(trustConsoleService.decide(TENANT, ACTOR, null, false,
                "provider-access-requests", "PAR-1", "APPROVED", "ok"))
                .thenReturn(Map.of("status", "completed"));

        ResponseEntity<Map<String, Object>> resp = controller().decide(
                TENANT, ACTOR, REQ, CORR, null, null,
                "provider-access-requests", "PAR-1",
                Map.of("decision", "APPROVED", "note", "ok"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(trustConsoleService).decide(TENANT, ACTOR, null, false,
                "provider-access-requests", "PAR-1", "APPROVED", "ok");
    }

    @Test
    void facilityIdHeaderTogglesFacilityContext() {
        when(trustConsoleService.summary(TENANT, ACTOR, "prov-1", true))
                .thenReturn(Map.of("integrationStatus", "live"));

        ResponseEntity<Map<String, Object>> resp =
                controller().summary(TENANT, ACTOR, REQ, CORR, "prov-1", "facility-9");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(trustConsoleService).summary(TENANT, ACTOR, "prov-1", true);
    }
}
