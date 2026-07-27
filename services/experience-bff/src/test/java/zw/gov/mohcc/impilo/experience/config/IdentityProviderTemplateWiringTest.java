package zw.gov.mohcc.impilo.experience.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anything that talks to Keycloak must do so on the NON-intercepted {@code idpRestTemplate}.
 *
 * <p>Stated as a DISCOVERY RULE, not a list of the three classes that need it today. A list would
 * pass forever while a fourth Keycloak-calling class quietly rode {@code serviceRestTemplate} —
 * which is exactly how the original defect survived: everything that could have caught it was
 * enumerated rather than derived. The rule here is "declares a KEYCLOAK_URL-bound field", so a new
 * IdP caller is in scope the moment it is written.</p>
 *
 * <p>Why it matters, beyond header hygiene: the interceptor forwards the caller's Authorization,
 * and these classes call Keycloak's token endpoint to MINT their own credential. A
 * credential-minting call must never inherit ambient credentials. The transport-level proof that
 * the IdP template ships nothing lives in {@link AuthorizationHeaderPrecedenceLoopbackTest}; this
 * test pins WHO is on it.</p>
 */
class IdentityProviderTemplateWiringTest {

    private static final String BASE_PACKAGE = "zw.gov.mohcc.impilo.experience";
    private static final String IDP_QUALIFIER = "idpRestTemplate";

    @Test
    void everyKeycloakCallingBeanTakesTheNonInterceptedIdpTemplate() throws Exception {
        List<Class<?>> keycloakCallers = keycloakCallingClasses();

        // Guard the guard: if the discovery rule silently matches nothing, this test would pass
        // while asserting about an empty set. The three known callers must be found.
        assertTrue(keycloakCallers.size() >= 3,
                "the discovery rule found " + keycloakCallers.size() + " Keycloak-calling classes ("
                        + keycloakCallers + ") — expected at least KeycloakAdminClient, "
                        + "AdminUserController and AuthSessionController. A rule that matches "
                        + "nothing is a test that asserts nothing.");

        List<String> violations = new ArrayList<>();
        for (Class<?> type : keycloakCallers) {
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                for (Parameter p : ctor.getParameters()) {
                    if (!RestTemplate.class.equals(p.getType())) {
                        continue;
                    }
                    Qualifier qualifier = p.getAnnotation(Qualifier.class);
                    if (qualifier == null || !IDP_QUALIFIER.equals(qualifier.value())) {
                        violations.add(type.getSimpleName() + " takes a RestTemplate without "
                                + "@Qualifier(\"" + IDP_QUALIFIER + "\") — it would ride the trust-header "
                                + "interceptor and ship ~30 Impilo headers, plus the caller's bearer, "
                                + "to the identity provider");
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    /**
     * The other half: nothing may reintroduce the intercepted template into an IdP caller by
     * name. Belt and braces against a future edit that keeps the qualifier annotation but renames
     * the parameter back, or adds a second unqualified RestTemplate field.
     */
    @Test
    void noKeycloakCallingBeanReferencesTheServiceTemplateByName() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : keycloakCallingClasses()) {
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                for (Parameter p : ctor.getParameters()) {
                    if (RestTemplate.class.equals(p.getType()) && "serviceRestTemplate".equals(p.getName())) {
                        violations.add(type.getSimpleName()
                                + " still names its RestTemplate parameter 'serviceRestTemplate'");
                    }
                }
            }
        }
        assertFalse(violations.size() > 0, String.join("\n", violations));
    }

    /** Classes binding a {@code KEYCLOAK_URL} @Value — i.e. classes that address the IdP directly. */
    private static List<Class<?>> keycloakCallingClasses() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

        List<Class<?>> found = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            String name = candidate.getBeanClassName();
            if (name == null) {
                continue;
            }
            Class<?> type;
            try {
                type = Class.forName(name);
            } catch (Throwable t) {
                continue;
            }
            for (Field f : type.getDeclaredFields()) {
                Value v = f.getAnnotation(Value.class);
                if (v != null && v.value().contains("KEYCLOAK_URL")) {
                    found.add(type);
                    break;
                }
            }
        }
        return found;
    }
}
