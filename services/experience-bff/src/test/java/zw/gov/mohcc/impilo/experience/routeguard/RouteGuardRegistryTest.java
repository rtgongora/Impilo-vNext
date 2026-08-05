package zw.gov.mohcc.impilo.experience.routeguard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real generated projection on the classpath, not a fixture.
 *
 * <p>A fixture would pass forever while the artefact the BFF actually reads rotted. These
 * assertions are statements about the live registry, so they go red if the export degrades.</p>
 */
@DisplayName("RouteGuardRegistry")
class RouteGuardRegistryTest {

    private final RouteGuardRegistry registry = new RouteGuardRegistry();

    @Test
    @DisplayName("loads the whole registry, not a truncated one")
    void loadsWholeRegistry() {
        // If the exporter silently emitted a handful of routes, everything below would still
        // pass by admitting unknown paths. The size floor is what makes the rest meaningful.
        assertThat(registry.size()).isGreaterThan(800);
    }

    @Nested
    @DisplayName("the defect this exists to prevent")
    class TheDefect {

        @Test
        @DisplayName("/coverage refuses the clinical roles the provider hub is handed to")
        void coverageRefusesClinicians() {
            // The original bug: the provider channels hub offered /coverage, which admits only
            // ADMIN. If this ever returns true, the filter has gone blind and the hub is free to
            // start offering it again.
            assertThat(registry.admits("/coverage", Set.of("CLINICIAN"))).isFalse();
            assertThat(registry.admits("/coverage", Set.of("NURSE"))).isFalse();
            assertThat(registry.admits("/coverage", Set.of("PHARMACIST"))).isFalse();
        }

        @Test
        @DisplayName("/ruvimbo/provider — the replacement — admits them")
        void ruvimboProviderAdmitsClinicians() {
            assertThat(registry.admits("/ruvimbo/provider", Set.of("CLINICIAN"))).isTrue();
            assertThat(registry.admits("/ruvimbo/provider", Set.of("NURSE"))).isTrue();
        }
    }

    @Nested
    @DisplayName("role matching")
    class RoleMatching {

        @Test
        @DisplayName("a role outside the required group is refused")
        void refusesOutsideGroup() {
            assertThat(registry.admits("/admin", Set.of("CLINICIAN"))).isFalse();
            assertThat(registry.admits("/developer/sandbox", Set.of("NURSE"))).isFalse();
            assertThat(registry.admits("/omnichannel", Set.of("CLINICIAN"))).isFalse();
            assertThat(registry.admits("/public-health/surveillance", Set.of("CLINICIAN"))).isFalse();
        }

        @Test
        @DisplayName("a role inside the required group is admitted")
        void admitsInsideGroup() {
            assertThat(registry.admits("/admin", Set.of("SYSTEM_ADMIN"))).isTrue();
            assertThat(registry.admits("/admin", Set.of("FACILITY_ADMIN"))).isTrue();
            assertThat(registry.admits("/public-health/surveillance", Set.of("CHW"))).isTrue();
            assertThat(registry.admits("/registry-admin", Set.of("HIE_ADMIN"))).isTrue();
        }

        @Test
        @DisplayName("SUPER_ADMIN inherits groups that grant SYSTEM_ADMIN or DEVELOPER")
        void superAdminInherits() {
            // Proves expandRoleGroup() survived the export — SUPER_ADMIN is nowhere in the raw
            // group literals in role-groups.ts, it is added by expansion.
            assertThat(registry.admits("/admin", Set.of("SUPER_ADMIN"))).isTrue();
            assertThat(registry.admits("/developer/sandbox", Set.of("SUPER_ADMIN"))).isTrue();
        }

        @Test
        @DisplayName("any one held role is enough")
        void anyRoleSuffices() {
            assertThat(registry.admits("/admin", Set.of("CITIZEN", "CLINICIAN", "SYSTEM_ADMIN")))
                    .isTrue();
        }

        @Test
        @DisplayName("no roles cannot open a role-guarded route")
        void noRolesRefused() {
            assertThat(registry.admits("/admin", Set.of())).isFalse();
            assertThat(registry.admits("/admin", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("guard kinds")
    class GuardKinds {

        @Test
        @DisplayName("an auth-guarded route is admitted — role is not its dimension")
        void authGuardAdmits() {
            // These are real hub destinations. Filtering them out would delete working links.
            assertThat(registry.admits("/registry", Set.of("CLINICIAN"))).isTrue();
            assertThat(registry.admits("/reports", Set.of("NURSE"))).isTrue();
            assertThat(registry.admits("/home/credentials", Set.of("NURSE"))).isTrue();
            assertThat(registry.admits("/settings/security", Set.of("PHARMACIST"))).isTrue();
        }

        @Test
        @DisplayName("an unrecognised path is admitted rather than silently hidden")
        void unknownPathAdmitted() {
            assertThat(registry.admits("/not-a-real-route-anywhere", Set.of("CLINICIAN"))).isTrue();
            assertThat(registry.admits(null, Set.of("CLINICIAN"))).isTrue();
            assertThat(registry.admits("", Set.of("CLINICIAN"))).isTrue();
        }
    }

    @Nested
    @DisplayName("path matching mirrors matchRouteDefinition()")
    class PathMatching {

        @Test
        @DisplayName("a [param] segment matches both the template and a concrete id")
        void dynamicSegments() {
            // The hub catalogue ships the template form verbatim, so both must resolve.
            assertThat(registry.admits("/public-health/site-registry/[siteId]", Set.of("CLINICIAN")))
                    .isFalse();
            assertThat(registry.admits("/public-health/site-registry/site-42", Set.of("CLINICIAN")))
                    .isFalse();
            assertThat(registry.admits("/public-health/site-registry/site-42", Set.of("CHW")))
                    .isTrue();
        }

        @Test
        @DisplayName("a [param] matches one segment only, never across a slash")
        void dynamicSegmentDoesNotSpanSlashes() {
            // /ai-governance/models/[id] is ADMIN. A greedy pattern would also swallow a deeper
            // path and refuse it on the wrong route's requirement.
            assertThat(registry.admits("/ai-governance/models/m-1", Set.of("CLINICIAN"))).isFalse();
            assertThat(registry.admits("/ai-governance/models/m-1/deep/unknown", Set.of("CLINICIAN")))
                    .isTrue();
        }

        @Test
        @DisplayName("first match wins — an earlier [param] route shadows a later static sibling")
        void firstMatchWins() {
            // Deliberately a fixture. The real registry holds exactly one shadowing pair today
            // (/workspace/[id] over /workspace/aggregate) and neither side is role-guarded, so
            // ordering cannot change any admits() answer on real data — a test against it would
            // pass whether or not order were preserved. Here the shadowing route is ADMIN, so
            // sorting or de-duplicating the export would turn this red.
            RouteGuardRegistry ordered = new RouteGuardRegistry("route-guards-ordering-fixture.json");

            assertThat(ordered.admits("/thing/public", Set.of("CLINICIAN")))
                    .as("/thing/[id] is declared first and requires ADMIN, so it wins")
                    .isFalse();
            assertThat(ordered.admits("/thing/public", Set.of("SYSTEM_ADMIN"))).isTrue();
            assertThat(ordered.admits("/other/public", Set.of("CLINICIAN")))
                    .as("nothing shadows this one")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("a missing projection fails startup instead of admitting everything")
    void missingProjectionFailsLoudly() {
        // Fail-open here would restore the defect invisibly: every hub would serve every link
        // again, and nothing would look wrong.
        assertThatThrownBy(() -> new RouteGuardRegistry("no-such-route-guards.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("generate:route-guards");
    }

    @Test
    @DisplayName("every web_path the provider hubs ship resolves against the registry")
    void hubCataloguePathsAreKnown() {
        // Guards against a hub quietly pointing at a path that no longer exists: such a path is
        // admitted (unknown paths are), so nothing else in this file would catch it.
        List<String> hubPaths = List.of(
                "/admin", "/registry", "/registry-admin", "/organization-admin", "/public-health",
                "/id-services", "/access", "/ai-governance",
                "/operations", "/operations/vito", "/operations/butano", "/operations/assets",
                "/operations/equipment", "/reports", "/reports/facility", "/reports/clinical",
                "/reports/operational", "/reports/custom", "/reports/[id]",
                "/developer", "/developer/api-catalog", "/developer/clients", "/developer/sandbox",
                "/settings", "/settings/account", "/settings/security", "/settings/notifications",
                "/settings/display", "/settings/integrations", "/settings/privacy",
                "/omnichannel", "/ruvimbo/provider", "/home/credentials",
                "/public-health/surveillance", "/public-health/campaigns",
                "/public-health/site-registry", "/public-health/site-registry/[siteId]");

        assertThat(hubPaths).allSatisfy(path ->
                assertThat(registry.isRegistered(path))
                        .as("hub offers %s, which is in no route registry entry", path)
                        .isTrue());
    }
}
