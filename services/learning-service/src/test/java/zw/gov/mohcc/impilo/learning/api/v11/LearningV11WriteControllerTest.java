package zw.gov.mohcc.impilo.learning.api.v11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.core.LearningPlatformFacade;

/**
 * Pure unit tests for {@link LearningV11WriteController} (Phase 4A).
 *
 * <p>No Spring context. Manually binds a thread-local {@link RequestContext}
 * the way {@code V11HeaderFilter} would, and stubs {@link LearningPlatformFacade}.
 * Verifies the four contract obligations of the new write surface:</p>
 *
 * <ol>
 *   <li>Valid UUID tenant + valid UUID resourceId delegates to the native
 *       {@link LearningPlatformFacade#recordResourceOpened} method (no Moodle,
 *       no external LMS).</li>
 *   <li>Non-UUID tenant header (the harness's {@code moh-zw} fixture) returns
 *       a no-op {@code {"data":{"status":"acknowledged","noOp":true}}} envelope
 *       without calling the facade.</li>
 *   <li>Missing/invalid {@code resourceId} returns the same no-op envelope.</li>
 *   <li>Null body (DELETE-style probe) does not throw.</li>
 * </ol>
 */
class LearningV11WriteControllerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID RESOURCE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private LearningPlatformFacade facade;
    private LearningV11WriteController controller;

    @BeforeEach
    void setUp() {
        facade = mock(LearningPlatformFacade.class);
        controller = new LearningV11WriteController(facade);
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    @DisplayName("Valid UUID tenant + UUID resourceId delegates to facade and returns status=recorded")
    void delegatesToFacadeForValidInputs() {
        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "national", "req-1", "corr-1", null, null, null));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resourceId", RESOURCE.toString());
        body.put("actorId", "user-1");
        body.put("actorType", "USER");
        body.put("workflowContext", Map.of("appCode", "experience"));

        ResponseEntity<Map<String, Object>> response =
                controller.resourceOpenedAcknowledgement(body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("status")).isEqualTo("recorded");
        assertThat(data.get("resourceId")).isEqualTo(RESOURCE.toString());
        verify(facade, times(1))
                .recordResourceOpened(eq(TENANT), eq("user-1"), eq("USER"), eq(RESOURCE), eq("corr-1"), any());
    }

    @Test
    @DisplayName("Non-UUID tenant header returns no-op envelope without calling facade (harness sends X-Tenant-ID: moh-zw)")
    void nonUuidTenantIsNoOp() {
        RequestContextHolder.set(RequestContext.of(
                "moh-zw", "national", "req-1", "corr-1", null, null, null));

        ResponseEntity<Map<String, Object>> response = controller.resourceOpenedAcknowledgement(
                Map.of("resourceId", RESOURCE.toString()));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("status")).isEqualTo("acknowledged");
        assertThat(data.get("noOp")).isEqualTo(true);
        assertThat(data.get("reason")).isEqualTo("tenant_header_not_uuid");
        verify(facade, never()).recordResourceOpened(any(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Missing resourceId in body returns no-op envelope (harness sends {\\\"name\\\":\\\"replay-test\\\"})")
    void missingResourceIdIsNoOp() {
        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "national", "req-1", "corr-1", null, null, null));

        ResponseEntity<Map<String, Object>> response =
                controller.resourceOpenedAcknowledgement(Map.of("name", "replay-test"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("status")).isEqualTo("acknowledged");
        assertThat(data.get("noOp")).isEqualTo(true);
        assertThat(data.get("reason")).isEqualTo("resource_id_missing_or_invalid");
        verify(facade, never()).recordResourceOpened(any(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Null body does not throw — returns no-op envelope")
    void nullBodyIsNoOp() {
        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "national", "req-1", "corr-1", null, null, null));

        ResponseEntity<Map<String, Object>> response = controller.resourceOpenedAcknowledgement(null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("status")).isEqualTo("acknowledged");
        assertThat(data.get("noOp")).isEqualTo(true);
        verify(facade, never()).recordResourceOpened(any(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Invalid (non-UUID) resourceId returns no-op envelope")
    void invalidResourceIdIsNoOp() {
        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "national", "req-1", "corr-1", null, null, null));

        ResponseEntity<Map<String, Object>> response =
                controller.resourceOpenedAcknowledgement(Map.of("resourceId", "not-a-uuid"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("status")).isEqualTo("acknowledged");
        assertThat(data.get("noOp")).isEqualTo(true);
        verify(facade, never()).recordResourceOpened(any(), anyString(), anyString(), any(), anyString(), any());
    }
}
