package zw.gov.mohcc.impilo.gl.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the general-ledger write gate: coverage, refusal, registration, and that it does not mask
 * downstream failures.
 *
 * <p>No Spring context on purpose — a guard test that needs a database to run is a guard test that
 * stops running.</p>
 */
class GlWriteGuardTest {

    private final GlWriteGuardInterceptor interceptor = new GlWriteGuardInterceptor("SHADOW");

    @AfterEach
    void clear() {
        TrustContextHolder.clear();
    }

    // ---------- coverage ----------

    private static final List<Class<? extends Annotation>> WRITES =
            List.of(PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    private static List<String> mappedWritePaths() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> paths = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("zw.gov.mohcc.impilo.gl")) {
            Class<?> type;
            try {
                type = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            RequestMapping base = type.getAnnotation(RequestMapping.class);
            String prefix = base != null && base.value().length > 0 ? base.value()[0] : "";
            for (Method m : type.getDeclaredMethods()) {
                for (var ann : WRITES) {
                    Annotation a = m.getAnnotation(ann);
                    if (a == null) {
                        continue;
                    }
                    String[] v = switch (a) {
                        case PostMapping p -> p.value();
                        case PutMapping p -> p.value();
                        case PatchMapping p -> p.value();
                        case DeleteMapping p -> p.value();
                        default -> new String[0];
                    };
                    String sub = v.length > 0 ? v[0] : "";
                    paths.add(sub.isEmpty() ? prefix
                            : prefix + (sub.startsWith("/") ? sub : "/" + sub));
                }
            }
        }
        return paths;
    }

    @Test
    @DisplayName("the scan finds the ledger's writes — a scan that finds none reports OK")
    void anchorTheScan() {
        // Measured 2026-08-08: 12 write mappings across 5 controllers.
        assertTrue(mappedWritePaths().size() >= 8,
                "expected >= 8 mapped writes, found " + mappedWritePaths().size()
                        + ". The scan is broken, not the ledger suddenly small.");
    }

    @Test
    @DisplayName("no ledger write admits a CITIZEN or CAREGIVER")
    void noWriteAdmitsACitizen() {
        List<String> bad = new ArrayList<>();
        for (String p : mappedWritePaths()) {
            String c = p.replaceAll("\\{[^}]+}", "11111111-1111-4111-8111-111111111111");
            if (GlWriteAuthorization.isExempt(c)) {
                continue;
            }
            var d = GlWriteAuthorization.dutyFor(c);
            if (d.actorTypes().contains("CITIZEN") || d.actorTypes().contains("CAREGIVER")) {
                bad.add(p + " -> " + d.actorTypes());
            }
        }
        assertTrue(bad.isEmpty(), "these ledger writes admit a citizen: " + bad);
    }

    // ---------- enforcement ----------

    private void actingAs(String actorType) {
        if (actorType == null) {
            TrustContextHolder.clear();
            return;
        }
        TrustContextHolder.set(new TrustContext(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                "actor-1", actorType, "OPERATIONS", null,
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    private void call(String method, String path) {
        var req = new MockHttpServletRequest(method, path);
        req.setRequestURI(path);
        interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
    }

    private void assertRefused(String path, String actor) {
        actingAs(actor);
        var ex = assertThrows(ResponseStatusException.class, () -> call("POST", path),
                path + " must refuse " + actor);
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    private void assertAllowed(String path, String actor) {
        actingAs(actor);
        assertDoesNotThrow(() -> call("POST", path), path + " must admit " + actor);
    }

    @Test
    @DisplayName("closing a period, year-end and the chart of accounts are back-office only")
    void theNineThatWereOpenAreNowGated() {
        for (String path : List.of(
                "/internal/v1/gl/accounts",
                "/internal/v1/gl/accounts/seed",
                "/internal/v1/gl/budgets",
                "/internal/v1/gl/budgets/refresh-actuals",
                "/internal/v1/gl/periods/ensure",
                "/internal/v1/gl/periods/abc/close",
                "/internal/v1/gl/periods/year-end",
                "/internal/v1/gl/trial-balance/snapshot")) {
            assertRefused(path, "CITIZEN");
            assertRefused(path, "CAREGIVER");
            assertRefused(path, "PROVIDER");
            assertAllowed(path, "OPERATOR");
            assertAllowed(path, "SYSTEM");
        }
    }

    @Test
    @DisplayName("no trust context at all is refused, never waved through")
    void absentContextRefused() {
        assertRefused("/internal/v1/gl/periods/year-end", null);
    }

    @Test
    @DisplayName("reads are untouched and the v1.1 probe stays exempt")
    void readsAndProbeNotGated() {
        actingAs("CITIZEN");
        assertDoesNotThrow(() -> call("GET", "/internal/v1/gl/accounts"));
        assertDoesNotThrow(() -> call("POST", "/internal/v1/test-command"));
    }

    /**
     * Regression carried over from costa: re-guarding the ERROR dispatch rewrites a real downstream
     * failure as a bare 403 and hides the actual fault.
     */
    @Test
    @DisplayName("the ERROR dispatch is not re-guarded")
    void errorDispatchNotReGuarded() {
        TrustContextHolder.clear();
        var req = new MockHttpServletRequest("POST", "/internal/v1/gl/periods/year-end");
        req.setRequestURI("/internal/v1/gl/periods/year-end");
        req.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
        assertDoesNotThrow(() -> interceptor.preHandle(req, new MockHttpServletResponse(), new Object()));
    }

    /**
     * Every other test builds the interceptor itself, so all of them would pass with the guard
     * registered nowhere. This asserts the half that actually puts it on the request path.
     */
    @Test
    @DisplayName("the interceptor is registered — not merely instantiable")
    void registered() {
        var registry = new InterceptorRegistry();
        new GlWriteGuardConfig().addInterceptors(registry);
        var m = ReflectionUtils.findMethod(registry.getClass(), "getInterceptors");
        ReflectionUtils.makeAccessible(m);
        @SuppressWarnings("unchecked")
        var list = (List<Object>) ReflectionUtils.invokeMethod(m, registry);
        boolean found = list.stream().anyMatch(i -> i instanceof MappedInterceptor mi
                ? mi.getInterceptor() instanceof GlWriteGuardInterceptor
                : i instanceof GlWriteGuardInterceptor);
        assertTrue(found, "GlWriteGuardInterceptor is not registered; the ledger gate would never run");
    }
}
