package zw.gov.mohcc.impilo.experience.service.provider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import zw.gov.mohcc.impilo.experience.client.OneUiShellMobileHubClient;
import zw.gov.mohcc.impilo.experience.config.BffProviderHubsProperties;
import zw.gov.mohcc.impilo.experience.routeguard.RouteGuardRegistry;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The provider hubs must only offer sections the caller's roles can actually open.
 *
 * <p>Drives the real filter through {@code SecurityContextHolder}, the way a request does, against
 * the real generated route-guard projection — not a stubbed registry.</p>
 */
@DisplayName("ProviderMobileHubService role filtering")
class ProviderMobileHubServiceRoleFilterTest {

    private ProviderMobileHubService service;

    @BeforeEach
    void setUp() {
        BffProviderHubsProperties properties = new BffProviderHubsProperties();
        properties.setMode(BffProviderHubsProperties.Mode.stub);
        service = new ProviderMobileHubService(
                properties,
                mock(OneUiShellMobileHubClient.class),
                new RouteGuardRegistry());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void signInWith(String... realmRoles) {
        var authorities = Arrays.stream(realmRoles)
                // Keycloak realm roles reach the BFF as ROLE_<name>; the filter must strip it.
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", "n/a", authorities));
    }

    private static Map<String, Object> section(String id, String webPath) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", id);
        m.put("web_path", webPath);
        return m;
    }

    /** The admin-registry hub catalogue, as its controller ships it. */
    private static List<Map<String, Object>> adminRegistryStub() {
        return List.of(
                section("admin", "/admin"),
                section("registry", "/registry"),
                section("registry_admin", "/registry-admin"),
                section("organization_admin", "/organization-admin"),
                section("public_health", "/public-health"),
                section("id_services", "/id-services"),
                section("access", "/access"),
                section("ai_governance", "/ai-governance"));
    }

    private static List<String> pathsOf(List<Map<String, Object>> sections) {
        return sections.stream().map(s -> s.get("web_path").toString()).toList();
    }

    @Test
    @DisplayName("a clinician is offered only the sections their roles open")
    void clinicianSeesOnlyReachableSections() {
        signInWith("CLINICIAN");

        List<Map<String, Object>> sections = service.sectionsForHub("admin-registry", adminRegistryStub());

        // /registry is auth-guarded, so it stays. Everything else in this hub is role-guarded
        // against roles a clinician does not hold, and each one was a bounce back to /home.
        assertThat(pathsOf(sections)).containsExactly("/registry");
    }

    @Test
    @DisplayName("a system admin still gets the whole hub")
    void adminSeesEverything() {
        signInWith("SYSTEM_ADMIN");

        List<Map<String, Object>> sections = service.sectionsForHub("admin-registry", adminRegistryStub());

        assertThat(pathsOf(sections)).isEqualTo(pathsOf(adminRegistryStub()));
    }

    @Test
    @DisplayName("a public-health officer gets their sections and not the admin ones")
    void publicHealthOfficerSeesTheirOwn() {
        signInWith("PUBLIC_HEALTH_OFFICER");

        List<Map<String, Object>> sections = service.sectionsForHub("admin-registry", adminRegistryStub());

        assertThat(pathsOf(sections)).containsExactly("/registry", "/public-health");
    }

    @Test
    @DisplayName("the channels hub no longer offers a coverage console clinicians cannot open")
    void channelsHubCoverageIsReachable() {
        signInWith("CLINICIAN");

        List<Map<String, Object>> sections = service.sectionsForHub("professional-channels", List.of(
                section("omnichannel", "/omnichannel"),
                section("coverage", "/ruvimbo/provider"),
                section("home_credentials", "/home/credentials")));

        // /omnichannel is ADMIN and drops out; the corrected coverage target survives.
        assertThat(pathsOf(sections)).containsExactly("/ruvimbo/provider", "/home/credentials");
    }

    @Test
    @DisplayName("the pre-fix coverage target would have been withheld from the same caller")
    void preFixCoverageTargetWouldBeWithheld() {
        // The negative control for the whole feature: had the catalogue still pointed at
        // /coverage, this filter would have refused it rather than shipping a dead link.
        signInWith("CLINICIAN");

        List<Map<String, Object>> sections = service.sectionsForHub("professional-channels", List.of(
                section("coverage", "/coverage")));

        assertThat(sections).isEmpty();
    }

    @Test
    @DisplayName("an unauthenticated caller is offered no role-guarded section")
    void unauthenticatedGetsNoRoleGuardedSections() {
        // The endpoint itself is authenticated(), so this is defence in depth rather than the
        // primary gate — but a filter that silently admitted everything without a principal
        // would be worse than no filter, because it would look like it was working.
        SecurityContextHolder.clearContext();

        List<Map<String, Object>> sections = service.sectionsForHub("admin-registry", adminRegistryStub());

        assertThat(pathsOf(sections)).containsExactly("/registry");
    }

    @Test
    @DisplayName("a section with no web_path is kept rather than silently dropped")
    void sectionWithoutPathSurvives() {
        signInWith("CLINICIAN");
        Map<String, Object> odd = new LinkedHashMap<>();
        odd.put("id", "no-path");
        odd.put("title", "No path");

        List<Map<String, Object>> sections = service.sectionsForHub("admin-registry", List.of(odd));

        assertThat(sections).hasSize(1);
    }
}
