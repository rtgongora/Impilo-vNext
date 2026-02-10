package zw.gov.mohcc.impilo.vito.migration.v11.wave21;

import zw.gov.mohcc.impilo.sharedkernel.events.EmitMode;
import zw.gov.mohcc.impilo.vito.core.FederationAuthorityGuard;
import zw.gov.mohcc.impilo.vito.core.FederationAuthorityGuard.FederationNotAuthorizedException;
import zw.gov.mohcc.impilo.vito.events.VitoEventMapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Wave 2.1 Standalone Runner — offline-safe, no JUnit, no Spring.
 *
 * Exercises the same assertions as the 3 JUnit test classes:
 *   A) V11PatientsController route mapping (reflection)
 *   B) DualEmitPolicy precedence (EMIT_MODE overrides yml)
 *   C) FederationAuthorityGuard legacy safety
 *
 * Exit code 0 = all pass, 1 = at least one failure.
 *
 * Usage:  java -cp <classpath> ...wave21.Wave21StandaloneRunner
 */
public class Wave21StandaloneRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Wave 2.1 Standalone Runner ===\n");

        // ---- A: V11PatientsController ----
        testA_classExists();
        testA_hasRestController();
        testA_requestMapping();
        testA_mergeMethodExists();

        // ---- B: DualEmitPolicy precedence ----
        testB_systemPropertyOverridesYml();
        testB_ymlUsedWhenEnvNotSet();
        testB_defaultIsDual();
        testB_invalidYmlFallsThrough();
        testB_emitLegacyContract();
        testB_emitV11Contract();

        // ---- C: FederationAuthorityGuard legacy safety ----
        testC_guardAllowsNational();
        testC_guardBlocksNonNational();
        testC_guardBlocksNull();
        testC_enforceFederationAuthorityIsPrivate();
        testC_currentPodIdReturnsNullOutsideContext();

        // ---- Summary ----
        System.out.println();
        System.out.printf("Results: %d passed, %d failed%n", passed, failed);
        System.exit(failed > 0 ? 1 : 0);
    }

    // =========================================================================
    // A: V11PatientsController
    // =========================================================================

    private static void testA_classExists() {
        try {
            Class.forName("zw.gov.mohcc.impilo.vito.api.v11.V11PatientsController");
            pass("A1 V11PatientsController class exists");
        } catch (ClassNotFoundException e) {
            fail("A1 V11PatientsController class exists", e);
        }
    }

    private static void testA_hasRestController() {
        try {
            Class<?> clazz = Class.forName("zw.gov.mohcc.impilo.vito.api.v11.V11PatientsController");
            Annotation rc = findAnnotation(clazz, "RestController");
            check(rc != null, "A2 @RestController present");
        } catch (Exception e) {
            fail("A2 @RestController present", e);
        }
    }

    private static void testA_requestMapping() {
        try {
            Class<?> clazz = Class.forName("zw.gov.mohcc.impilo.vito.api.v11.V11PatientsController");
            Annotation rm = findAnnotation(clazz, "RequestMapping");
            if (rm == null) { fail("A3 @RequestMapping present", null); return; }
            String[] value = annotationValue(rm);
            check(value != null && value.length > 0
                    && "/internal/v1/patients".equals(value[0]),
                    "A3 @RequestMapping(\"/internal/v1/patients\")");
        } catch (Exception e) {
            fail("A3 @RequestMapping(\"/internal/v1/patients\")", e);
        }
    }

    private static void testA_mergeMethodExists() {
        try {
            Class<?> clazz = Class.forName("zw.gov.mohcc.impilo.vito.api.v11.V11PatientsController");
            Method merge = findMethod(clazz, "merge");
            if (merge == null) { fail("A4 merge() method exists", null); return; }
            Annotation pm = findAnnotation(merge, "PostMapping");
            if (pm == null) { fail("A4 merge() @PostMapping present", null); return; }
            String[] paths = annotationValue(pm);
            check(paths != null && paths.length > 0 && "/merge".equals(paths[0]),
                    "A4 merge() @PostMapping(\"/merge\")");
        } catch (Exception e) {
            fail("A4 merge() @PostMapping(\"/merge\")", e);
        }
    }

    // =========================================================================
    // B: DualEmitPolicy precedence
    // =========================================================================

    private static void testB_systemPropertyOverridesYml() {
        try {
            System.setProperty("EMIT_MODE", "V1_1_ONLY");
            EmitMode result = VitoEventMapper.resolveEmitMode("LEGACY_ONLY");
            check(result == EmitMode.V1_1_ONLY, "B1 EMIT_MODE sys prop overrides yml");
        } catch (Exception e) {
            fail("B1 EMIT_MODE sys prop overrides yml", e);
        } finally {
            System.clearProperty("EMIT_MODE");
        }
    }

    private static void testB_ymlUsedWhenEnvNotSet() {
        try {
            EmitMode result = VitoEventMapper.resolveEmitMode("LEGACY_ONLY");
            check(result == EmitMode.LEGACY_ONLY, "B2 yml used when env not set");
        } catch (Exception e) {
            fail("B2 yml used when env not set", e);
        }
    }

    private static void testB_defaultIsDual() {
        try {
            EmitMode result = VitoEventMapper.resolveEmitMode(null);
            check(result == EmitMode.DUAL, "B3 default is DUAL");
        } catch (Exception e) {
            fail("B3 default is DUAL", e);
        }
    }

    private static void testB_invalidYmlFallsThrough() {
        try {
            EmitMode result = VitoEventMapper.resolveEmitMode("INVALID_VALUE");
            check(result == EmitMode.DUAL, "B4 invalid yml falls through to DUAL");
        } catch (Exception e) {
            fail("B4 invalid yml falls through to DUAL", e);
        }
    }

    private static void testB_emitLegacyContract() {
        try {
            check(VitoEventMapper.emitLegacy(EmitMode.LEGACY_ONLY)
                    && VitoEventMapper.emitLegacy(EmitMode.DUAL)
                    && !VitoEventMapper.emitLegacy(EmitMode.V1_1_ONLY),
                    "B5 emitLegacy contract");
        } catch (Exception e) {
            fail("B5 emitLegacy contract", e);
        }
    }

    private static void testB_emitV11Contract() {
        try {
            check(VitoEventMapper.emitV11(EmitMode.V1_1_ONLY)
                    && VitoEventMapper.emitV11(EmitMode.DUAL)
                    && !VitoEventMapper.emitV11(EmitMode.LEGACY_ONLY),
                    "B6 emitV11 contract");
        } catch (Exception e) {
            fail("B6 emitV11 contract", e);
        }
    }

    // =========================================================================
    // C: FederationAuthorityGuard legacy safety
    // =========================================================================

    private static void testC_guardAllowsNational() {
        try {
            new FederationAuthorityGuard().requireNationalPodForMerge("national");
            pass("C1 guard allows national pod");
        } catch (Exception e) {
            fail("C1 guard allows national pod", e);
        }
    }

    private static void testC_guardBlocksNonNational() {
        try {
            new FederationAuthorityGuard().requireNationalPodForMerge("pod-harare");
            fail("C2 guard blocks non-national pod (no exception thrown)", null);
        } catch (FederationNotAuthorizedException ex) {
            check("FEDERATION_NOT_AUTHORIZED".equals(ex.getMessage())
                    && "pod-harare".equals(ex.getPodId()),
                    "C2 guard blocks non-national pod");
        } catch (Exception e) {
            fail("C2 guard blocks non-national pod", e);
        }
    }

    private static void testC_guardBlocksNull() {
        try {
            new FederationAuthorityGuard().requireNationalPodForMerge(null);
            fail("C3 guard blocks null pod (no exception thrown)", null);
        } catch (FederationNotAuthorizedException ex) {
            pass("C3 guard blocks null pod");
        } catch (Exception e) {
            fail("C3 guard blocks null pod", e);
        }
    }

    private static void testC_enforceFederationAuthorityIsPrivate() {
        try {
            Class<?> ms = Class.forName("zw.gov.mohcc.impilo.vito.core.merge.MergeService");
            Method m = findMethod(ms, "enforceFederationAuthority");
            check(m != null && Modifier.isPrivate(m.getModifiers()),
                    "C4 enforceFederationAuthority() is private");
        } catch (Exception e) {
            fail("C4 enforceFederationAuthority() is private", e);
        }
    }

    private static void testC_currentPodIdReturnsNullOutsideContext() {
        try {
            Class<?> ms = Class.forName("zw.gov.mohcc.impilo.vito.core.merge.MergeService");
            Method m = findMethod(ms, "currentPodId");
            if (m == null) { fail("C5 currentPodId() exists", null); return; }
            m.setAccessible(true);
            Object result = m.invoke(null); // static method
            check(result == null, "C5 currentPodId() returns null outside request context");
        } catch (Exception e) {
            fail("C5 currentPodId() returns null outside request context", e);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void pass(String label) {
        System.out.printf("  PASS  %s%n", label);
        passed++;
    }

    private static void fail(String label, Exception e) {
        System.out.printf("  FAIL  %s%s%n", label,
                e != null ? " — " + e.getClass().getSimpleName() + ": " + e.getMessage() : "");
        failed++;
    }

    private static void check(boolean condition, String label) {
        if (condition) pass(label); else fail(label, null);
    }

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

    private static Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (name.equals(m.getName())) return m;
        }
        return null;
    }

    private static String[] annotationValue(Annotation a) {
        try {
            Method v = a.annotationType().getMethod("value");
            Object r = v.invoke(a);
            if (r instanceof String[] arr) return arr;
            if (r instanceof String s) return new String[]{s};
        } catch (Exception ignored) {
        }
        return null;
    }
}
