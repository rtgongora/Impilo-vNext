package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.intelligence.HealthIntelligenceService;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;
import zw.gov.mohcc.impilo.experience.workcontext.WorkContextResolutionService;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Assistant-notification scope is server-derived — Target Architecture v1.3.2
 * §40.1, backlog item 83, test A91.
 *
 * The endpoint previously took {@code work_mode}, {@code facility_id} and
 * {@code shift_id} from query parameters: the browser asserted its own
 * notification scope. These tests prove (1) scope now comes only from the
 * actor identity resolved through {@link WorkContextResolutionService};
 * (2) a request without a trusted actor identity is refused; and (3) — the
 * structural mutation guard — the handler method physically has no
 * {@code @RequestParam} inputs, so a client-asserted parameter cannot re-enter
 * without turning this suite red.
 */
class AssistantNotificationsScopeTest {

    private final HealthIntelligenceService intelligence = mock(HealthIntelligenceService.class);
    private final WorkContextResolutionService resolution = mock(WorkContextResolutionService.class);

    private AssistantNotificationsController controller() {
        return new AssistantNotificationsController(intelligence, null, null, null, resolution);
    }

    private static ResolvedWorkContext context(String facilityId, String mode, Map<String, Object> shift) {
        return new ResolvedWorkContext(
                "ctx-1", "facility", "VASHANDI", "src-1",
                facilityId, "org-1", null, null, "tmpl-1",
                List.of(), List.of(mode), mode, "resolved",
                "dept-1", "Casualty", null, null, null,
                shift, List.of(), "Parirenyatwa — Casualty", "today", 90);
    }

    @Test
    void scopeComesFromTheServerSideResolution_neverFromTheClient() {
        when(resolution.resolve(eq("actor-1"), any())).thenReturn(
                new WorkContextResolutionService.ResolutionOutcome(
                        List.of(context("FAC-REAL", "provider", Map.of("shift_id", "SHIFT-REAL"))),
                        List.of(), "ctx-1", false, null));
        when(intelligence.buildAssistantNotifications(any(), any(), any(), any()))
                .thenReturn(List.of());

        ResponseEntity<Map<String, Object>> res =
                controller().getNotifications("tenant-1", "actor-1");

        assertEquals(200, res.getStatusCode().value());
        // The decisive assertion: the intelligence layer is scoped by the
        // RESOLVED facility/mode/shift — values no client parameter can supply.
        verify(intelligence).buildAssistantNotifications("provider", "tenant-1", "FAC-REAL", "SHIFT-REAL");
    }

    @Test
    void missingActorIdentityIsRefused_notDefaultedAndNotClientScoped() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller().getNotifications("tenant-1", null));
        assertEquals(401, ex.getStatusCode().value());
        verifyNoInteractions(resolution, intelligence);
    }

    @Test
    void actorWithNoWorkContextGetsCitizenScope_notAnError() {
        when(resolution.resolve(eq("actor-2"), any())).thenReturn(
                new WorkContextResolutionService.ResolutionOutcome(
                        List.of(), List.of(), null, false, null));
        when(intelligence.buildAssistantNotifications(any(), any(), any(), any()))
                .thenReturn(List.of());

        ResponseEntity<Map<String, Object>> res =
                controller().getNotifications("tenant-1", "actor-2");

        assertEquals(200, res.getStatusCode().value());
        verify(intelligence).buildAssistantNotifications(null, "tenant-1", null, null);
    }

    @Test
    void mutationGuard_theHandlerHasNoRequestParamInputs() throws Exception {
        Method handler = null;
        for (Method m : AssistantNotificationsController.class.getDeclaredMethods()) {
            if (m.getName().equals("getNotifications")) handler = m;
        }
        assertNotNull(handler, "getNotifications handler must exist");
        for (Parameter p : handler.getParameters()) {
            assertFalse(p.isAnnotationPresent(RequestParam.class),
                    "client-asserted @RequestParam scope must never return to this endpoint: " + p);
        }
        // And specifically: the old parameter names are gone.
        assertEquals(2, handler.getParameterCount(),
                "handler takes exactly tenant + actor headers; work_mode/facility_id/shift_id were removed");
    }
}
