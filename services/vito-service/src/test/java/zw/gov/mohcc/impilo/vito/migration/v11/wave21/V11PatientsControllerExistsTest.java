package zw.gov.mohcc.impilo.vito.migration.v11.wave21;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test A: V11PatientsController exists and is mapped to /internal/v1/patients/merge.
 *
 * Reflection-based — no Spring context required.
 */
@DisplayName("Wave 2.1 — V11PatientsController route mapping (reflection)")
class V11PatientsControllerExistsTest {

    private static final String CONTROLLER_CLASS =
            "zw.gov.mohcc.impilo.vito.api.v11.V11PatientsController";

    @Test
    @DisplayName("V11PatientsController class exists on classpath")
    void classExists() throws ClassNotFoundException {
        Class<?> clazz = Class.forName(CONTROLLER_CLASS);
        assertNotNull(clazz, "V11PatientsController must exist");
    }

    @Test
    @DisplayName("Class has @RestController annotation")
    void hasRestControllerAnnotation() throws ClassNotFoundException {
        Class<?> clazz = Class.forName(CONTROLLER_CLASS);
        Annotation restController = findAnnotation(clazz, "RestController");
        assertNotNull(restController,
                "V11PatientsController must have @RestController annotation");
    }

    @Test
    @DisplayName("Class @RequestMapping is /internal/v1/patients")
    void hasRequestMappingToInternalV1Patients() throws ClassNotFoundException {
        Class<?> clazz = Class.forName(CONTROLLER_CLASS);
        Annotation requestMapping = findAnnotation(clazz, "RequestMapping");
        assertNotNull(requestMapping, "V11PatientsController must have @RequestMapping");

        // Extract value from the annotation
        String[] value = getAnnotationValue(requestMapping);
        assertNotNull(value, "@RequestMapping must have a value");
        assertTrue(value.length > 0, "@RequestMapping must have at least one path");
        assertEquals("/internal/v1/patients", value[0],
                "@RequestMapping value must be /internal/v1/patients");
    }

    @Test
    @DisplayName("merge() method exists with @PostMapping(\"/merge\")")
    void mergeMethodExists() throws ClassNotFoundException {
        Class<?> clazz = Class.forName(CONTROLLER_CLASS);
        Method mergeMethod = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if ("merge".equals(m.getName())) {
                mergeMethod = m;
                break;
            }
        }
        assertNotNull(mergeMethod, "merge() method must exist");

        Annotation postMapping = findAnnotation(mergeMethod, "PostMapping");
        assertNotNull(postMapping, "merge() must have @PostMapping");

        String[] paths = getAnnotationValue(postMapping);
        assertNotNull(paths, "@PostMapping must have a value");
        assertTrue(paths.length > 0, "@PostMapping must have at least one path");
        assertEquals("/merge", paths[0], "@PostMapping value must be /merge");
    }

    // ---- Helpers ----

    private static Annotation findAnnotation(Class<?> clazz, String simpleName) {
        for (Annotation a : clazz.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals(simpleName)) return a;
        }
        return null;
    }

    private static Annotation findAnnotation(Method method, String simpleName) {
        for (Annotation a : method.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals(simpleName)) return a;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String[] getAnnotationValue(Annotation annotation) {
        try {
            Method valueMethod = annotation.annotationType().getMethod("value");
            Object result = valueMethod.invoke(annotation);
            if (result instanceof String[] arr) return arr;
            if (result instanceof String s) return new String[]{s};
        } catch (Exception ignored) {
        }
        return null;
    }
}
