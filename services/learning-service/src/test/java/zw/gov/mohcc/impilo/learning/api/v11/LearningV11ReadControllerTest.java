package zw.gov.mohcc.impilo.learning.api.v11;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for {@link LearningV11ReadController}.
 *
 * <p>No Spring context. Stubs out the {@link LearningPlatformFacade} and binds
 * a thread-local {@link RequestContext} the way {@code V11HeaderFilter} would
 * at runtime. Verifies that the new native v1.1 read endpoint:</p>
 * <ul>
 *   <li>delegates to the existing native facade (no Moodle, no external LMS);</li>
 *   <li>returns an empty {@code data.items} envelope when {@code subjectType}
 *       or {@code subjectId} is missing — keeping the surface side-effect-free
 *       for the golden-contract "all headers present passes through" assertion;</li>
 *   <li>returns an empty envelope when the tenant header is not a valid UUID
 *       (the harness sends {@code X-Tenant-ID: moh-zw}, not a UUID);</li>
 *   <li>returns the v1.1 envelope shape {@code {"data": {"items": [...]}}}.</li>
 * </ul>
 */
class LearningV11ReadControllerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private LearningPlatformFacade facade;
    private LearningV11ReadController controller;

    @BeforeEach
    void setUp() {
        facade = mock(LearningPlatformFacade.class);
        controller = new LearningV11ReadController(facade);
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    @DisplayName("Valid UUID tenant + subjectType + subjectId delegates to the native facade")
    void delegatesToFacadeForValidInputs() {
        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "national", "req-1", "corr-1", null, null, null));
        when(facade.listSubjectCompletions(TENANT, "USER", "subj-7", 25))
                .thenReturn(List.of(Map.of("completionId", "c1")));

        ResponseEntity<Map<String, Object>> response =
                controller.subjectCompletionsV11("USER", "subj-7", 25);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("subjectType")).isEqualTo("USER");
        assertThat(data.get("subjectId")).isEqualTo("subj-7");
        assertThat(data.get("limit")).isEqualTo(25);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("completionId")).isEqualTo("c1");

        verify(facade, times(1)).listSubjectCompletions(TENANT, "USER", "subj-7", 25);
    }

    @Test
    @DisplayName("Missing subjectType returns empty items without calling the facade")
    void missingSubjectTypeReturnsEmpty() {
        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "national", "req-1", "corr-1", null, null, null));

        ResponseEntity<Map<String, Object>> response =
                controller.subjectCompletionsV11(null, "subj-7", 25);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("items")).isEqualTo(List.of());
        verify(facade, never()).listSubjectCompletions(any(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Missing subjectId returns empty items without calling the facade")
    void missingSubjectIdReturnsEmpty() {
        RequestContextHolder.set(RequestContext.of(
                TENANT.toString(), "national", "req-1", "corr-1", null, null, null));

        ResponseEntity<Map<String, Object>> response =
                controller.subjectCompletionsV11("USER", "  ", 25);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("items")).isEqualTo(List.of());
        verify(facade, never()).listSubjectCompletions(any(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Non-UUID tenant header returns empty items without calling the facade (harness sends X-Tenant-ID: moh-zw)")
    void nonUuidTenantReturnsEmpty() {
        RequestContextHolder.set(RequestContext.of(
                "moh-zw", "national", "req-1", "corr-1", null, null, null));

        ResponseEntity<Map<String, Object>> response =
                controller.subjectCompletionsV11("USER", "subj-7", 25);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("items")).isEqualTo(List.of());
        verify(facade, never()).listSubjectCompletions(any(), anyString(), anyString(), anyInt());
    }
}
