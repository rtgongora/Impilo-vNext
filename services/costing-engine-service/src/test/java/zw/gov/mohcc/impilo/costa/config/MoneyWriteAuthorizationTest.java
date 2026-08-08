package zw.gov.mohcc.impilo.costa.config;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage anchor: every write this service maps must resolve to a duty that refuses a citizen.
 *
 * <p>This is what makes the duty map trustworthy. A map is only as good as its coverage, and
 * coverage is exactly what drifts — someone adds a controller, nobody adds a mapping, and the gate
 * "protecting the money service" quietly has a hole. Rather than list endpoints here (which would
 * drift the same way), this <b>scans the compiled controllers</b>, so a new write is in scope the
 * moment it is mapped.</p>
 *
 * <p>Deliberately no Spring context. This service has no H2 test profile — its only
 * {@code @SpringBootTest} is an IT that needs an external Postgres and self-skips without one — and
 * a guard test that cannot run without a database is a guard test that stops running. Classpath
 * scanning gives the same answer with no dependency on the persistence layer.</p>
 */
class MoneyWriteAuthorizationTest {

    private static final List<Class<? extends Annotation>> WRITE_ANNOTATIONS =
            List.of(PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    /** Every write path mapped by a controller in this service. */
    private static List<String> mappedWritePaths() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> paths = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("zw.gov.mohcc.impilo.costa")) {
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
        // Measured 2026-08-07: 85 distinct write mappings across 25 controllers. Pinned well below
        // that so adding endpoints does not fail the build, but high enough that a scan returning
        // nothing — a renamed base package, a changed annotation — fails loudly rather than
        // passing vacuously.
        assertTrue(paths.size() >= 60,
                "expected >= 60 mapped writes, found " + paths.size()
                        + ". The scan is broken, not the service suddenly small.");
    }

    @Test
    @DisplayName("no mapped write is reachable by a CITIZEN or CAREGIVER — the exposure P0 named")
    void noWriteAdmitsThePersonTheChargeIsAgainst() {
        List<String> offenders = new ArrayList<>();
        for (String path : mappedWritePaths()) {
            String p = concrete(path);
            if (MoneyWriteAuthorization.isExempt(p)) {
                continue;
            }
            var duty = MoneyWriteAuthorization.dutyFor(p);
            if (duty.actorTypes().contains("CITIZEN") || duty.actorTypes().contains("CAREGIVER")) {
                offenders.add(path + " -> " + duty.actorTypes());
            }
        }
        assertTrue(offenders.isEmpty(),
                "these writes admit the person the charge is against: " + offenders);
    }

    @Test
    @DisplayName("point-of-care capture admits PROVIDER; money decisions do not")
    void captureAndDecisionDifferWhereItMatters() {
        // A clinician raising a bill at the bedside must get through: five clinician-facing BFF
        // controllers call this, and the BFF forwards their PROVIDER actor type unchanged.
        assertTrue(MoneyWriteAuthorization.dutyFor("/costa/v1/bills/draft")
                .actorTypes().contains("PROVIDER"));
        assertTrue(MoneyWriteAuthorization.dutyFor("/costa/v1/estimate")
                .actorTypes().contains("PROVIDER"));

        for (String decision : List.of(
                "/costa/v1/bills/abc/approve",
                "/costa/v1/bills/abc/finalize",
                "/costa/v1/bills/abc/refund",
                "/costa/v1/waivers",
                "/internal/v1/finance/receivables/write-off",
                "/internal/v1/finance/budgets/managed",
                "/internal/v1/finance/reports/close-period")) {
            var duty = MoneyWriteAuthorization.dutyFor(decision);
            assertFalse(duty.actorTypes().contains("PROVIDER"),
                    decision + " must not admit PROVIDER");
            assertTrue(duty.actorTypes().equals(ActorTypeGuard.BACK_OFFICE_WRITERS),
                    decision + " must be back-office, was " + duty.actorTypes());
        }
    }

    @Test
    @DisplayName("an unlisted path defaults to the stricter set, so a new endpoint is gated")
    void unlistedPathsFailClosed() {
        var duty = MoneyWriteAuthorization.dutyFor("/costa/v1/some-endpoint-added-tomorrow");
        assertTrue(duty.actorTypes().equals(ActorTypeGuard.BACK_OFFICE_WRITERS));
        assertFalse(duty.actorTypes().contains("CITIZEN"));
    }
}
