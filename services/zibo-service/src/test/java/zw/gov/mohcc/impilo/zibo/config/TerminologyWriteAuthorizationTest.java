package zw.gov.mohcc.impilo.zibo.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage anchor: every write this service maps must resolve to a duty that refuses a clinician.
 *
 * <p>A duty map is only as good as its coverage, and coverage is exactly what drifts — someone adds
 * a controller, nobody adds a mapping, and the gate protecting national terminology quietly has a
 * hole. Rather than list endpoints here, which would drift the same way, this <b>scans the compiled
 * controllers</b>, so a new write is in scope the moment it is mapped.</p>
 */
class TerminologyWriteAuthorizationTest {

    private static final List<Class<? extends Annotation>> WRITE_ANNOTATIONS =
            List.of(PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    /** Every write path mapped by a controller in this service. */
    private static List<String> mappedWritePaths() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> paths = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("zw.gov.mohcc.impilo.zibo")) {
            Class<?> type;
            try {
                type = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            RequestMapping base = type.getAnnotation(RequestMapping.class);
            String prefix = base != null && base.value().length > 0 ? base.value()[0] : "";
            for (Method m : type.getDeclaredMethods()) {
                for (var ann : WRITE_ANNOTATIONS) {
                    Annotation a = m.getAnnotation(ann);
                    if (a == null) {
                        continue;
                    }
                    String[] value = switch (a) {
                        case PostMapping p -> p.value();
                        case PutMapping p -> p.value();
                        case PatchMapping p -> p.value();
                        case DeleteMapping p -> p.value();
                        default -> new String[0];
                    };
                    String sub = value.length > 0 ? value[0] : "";
                    String full = sub.isEmpty() ? prefix
                            : (prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix)
                                    + (sub.startsWith("/") ? sub : "/" + sub);
                    paths.add(full);
                }
            }
        }
        return paths;
    }

    /** Path variables replaced so the pattern matcher sees a concrete request path. */
    private static String concrete(String path) {
        return path.replaceAll("\\{[^}]+}", "11111111-1111-4111-8111-111111111111");
    }

    @Test
    @DisplayName("the scan finds a realistic number of writes — a scan that finds none reports OK")
    void anchorTheScanItself() {
        List<String> paths = mappedWritePaths();
        // Measured 2026-08-08: 22 write mappings before this change, 23 with /v1/concepts/rebuild.
        // Pinned below that so adding endpoints does not fail the build, but high enough that a
        // scan returning nothing — a renamed base package, a changed annotation — fails loudly
        // rather than passing vacuously on an empty collection.
        assertTrue(paths.size() >= 18,
                "expected >= 18 mapped writes, found " + paths.size()
                        + ". The scan is broken, not the service suddenly small.");
    }

    @Test
    @DisplayName("no governed write admits PROVIDER, CITIZEN or CAREGIVER")
    void noGovernedWriteAdmitsAConsumerOfTerminology() {
        List<String> offenders = new ArrayList<>();
        for (String path : mappedWritePaths()) {
            String p = concrete(path);
            if (TerminologyWriteAuthorization.isReadShaped(p)) {
                continue;
            }
            var duty = TerminologyWriteAuthorization.dutyFor(p);
            for (String forbidden : List.of("PROVIDER", "CITIZEN", "CAREGIVER")) {
                if (duty.actorTypes().contains(forbidden)) {
                    offenders.add(path + " -> " + duty.actorTypes());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "these terminology writes admit a consumer of the vocabulary: " + offenders);
    }

    @Test
    @DisplayName("an unlisted path gets the governed default rather than falling open")
    void unlistedPathsAreGatedNotOpen() {
        var duty = TerminologyWriteAuthorization.dutyFor("/v1/some/endpoint/added/tomorrow");

        assertEquals(ActorTypeGuard.BACK_OFFICE_WRITERS, duty.actorTypes());
        assertFalse(TerminologyWriteAuthorization.isReadShaped("/v1/some/endpoint/added/tomorrow"));
    }

    @Test
    @DisplayName("the artifact lifecycle transitions are all back-office")
    void lifecycleTransitionsAreBackOffice() {
        for (String governed : List.of(
                "/v1/artifacts",
                "/v1/artifacts/abc/publish",
                "/v1/artifacts/abc/deprecate",
                "/v1/artifacts/abc/retire",
                "/v1/packs",
                "/v1/packs/abc/publish",
                "/v1/assignments",
                "/v1/import/fhir-bundle",
                "/v1/import/csv",
                "/v1/map/rebuild",
                "/v1/concepts/rebuild",
                "/internal/v1/snapshots/artifacts/emit")) {
            var duty = TerminologyWriteAuthorization.dutyFor(governed);
            assertEquals(ActorTypeGuard.BACK_OFFICE_WRITERS, duty.actorTypes(),
                    governed + " must be back-office, was " + duty.actorTypes());
            assertFalse(TerminologyWriteAuthorization.isReadShaped(governed),
                    governed + " changes governed terminology and must not be read-shaped");
        }
    }

    @Test
    @DisplayName("the clinical path still validates — read-shaped POSTs are not gated")
    void clinicalValidationIsNotGated() {
        // These four are the live callers measured on 2026-08-08. If this test ever goes red because
        // one was moved into the governed set, terminology validation during care has been switched
        // off, which reads as a hardening and behaves as an outage.
        for (String clinical : List.of(
                "/api/v1/terminology/validate",   // BUTANO TerminologyValidationInterceptor
                "/v1/validate/coding",            // OROS, MSIKA, teleconsult response validation
                "/v1/validate/resource",
                "/v1/map",                        // ConceptMap translation lookup
                "/internal/v1/confidentiality/classify")) {
            assertTrue(TerminologyWriteAuthorization.isReadShaped(clinical),
                    clinical + " sits on the clinical path and must not need a governance duty");
        }
    }

    @Test
    @DisplayName("/v1/map is read-shaped but /v1/map/rebuild is not — the prefix must not leak")
    void rebuildIsNotCoveredByTheTranslationExemption() {
        assertTrue(TerminologyWriteAuthorization.isReadShaped("/v1/map"));
        assertFalse(TerminologyWriteAuthorization.isReadShaped("/v1/map/rebuild"),
                "rebuilding every mapping index is not a translation lookup");
        assertEquals(ActorTypeGuard.BACK_OFFICE_WRITERS,
                TerminologyWriteAuthorization.dutyFor("/v1/map/rebuild").actorTypes());
    }
}
