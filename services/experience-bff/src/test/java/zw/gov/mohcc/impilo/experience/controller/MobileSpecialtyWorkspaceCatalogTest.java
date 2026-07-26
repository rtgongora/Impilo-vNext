package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the honesty of the specialty workspace menu.
 *
 * <p>The route this covers used to return 10 specialties × 3 bare tool names with nothing to
 * distinguish a tool that works from one that has never existed. A menu entry is a promise, and
 * for clinical calculators a false promise is worse than a missing one: a clinician who believes
 * the estate carries a Parkland calculator may go looking for it during a resuscitation instead
 * of reaching for the wall chart.
 *
 * <p>The invariant is therefore not "the list is non-empty" but <b>no tool may advertise itself as
 * available without naming the route that serves it</b>, and every gap must carry a reason a
 * clinician can act on. If someone adds a tool label without wiring it, this test fails.
 */
class MobileSpecialtyWorkspaceCatalogTest {

    private static final MobileProviderExtendedController CONTROLLER =
            new MobileProviderExtendedController(null, null, null, null, null, null, null, null);

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> workspaces() {
        ResponseEntity<Map<String, Object>> response = CONTROLLER.getSpecialtyWorkspaces();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return (List<Map<String, Object>>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tools(Map<String, Object> workspace) {
        return (List<Map<String, Object>>) workspace.get("tools");
    }

    private static List<Map<String, Object>> allTools() {
        return workspaces().stream().flatMap(w -> tools(w).stream()).toList();
    }

    @Test
    void everyToolDeclaresAvailabilityAndMaturity() {
        Set<String> canonicalMaturity = Set.of("Live", "Partial", "Fixture", "Not wired", "Blocked");
        for (Map<String, Object> tool : allTools()) {
            assertThat(tool).as("tool keys for %s", tool.get("name"))
                    .containsKeys("id", "name", "available", "maturity", "route", "unavailable_reason");
            assertThat(tool.get("maturity")).as("maturity of %s is canonical", tool.get("name"))
                    .isIn(canonicalMaturity);
        }
    }

    @Test
    void noToolClaimsToBeAvailableWithoutARouteThatServesIt() {
        for (Map<String, Object> tool : allTools()) {
            if (Boolean.TRUE.equals(tool.get("available"))) {
                assertThat(tool.get("route")).as("available tool %s must name its route", tool.get("name"))
                        .isNotNull();
                assertThat((String) tool.get("route")).startsWith("/internal/v1/");
                assertThat(tool.get("maturity")).isEqualTo("Live");
            }
        }
    }

    @Test
    void everyUnavailableToolExplainsItselfRatherThanJustFailing() {
        for (Map<String, Object> tool : allTools()) {
            if (!Boolean.TRUE.equals(tool.get("available"))) {
                assertThat(tool.get("route")).as("unavailable tool %s must not name a route", tool.get("name"))
                        .isNull();
                assertThat((String) tool.get("unavailable_reason"))
                        .as("unavailable tool %s must give a reason", tool.get("name"))
                        .isNotBlank();
            }
        }
    }

    /**
     * The named tools whose absence is a deliberate clinical decision rather than a backlog item.
     * Burns %TBSA and Parkland are leased to the Emergency, Resuscitation and Acute Care pack
     * (docs/registry/iatg-emergency-leases.md §5b) after a private implementation shipped
     * adult-only proportions and an unclocked 24-hour total. If any of these flips to available
     * here, a fourth private implementation has appeared.
     */
    @Test
    void leasedClinicalCalculatorsStayBlocked() {
        Set<String> leased = Set.of("Lund-Browder Chart", "Fluid Calculator", "Parkland Formula",
                "ACLS Protocol", "Drug Calculator", "Defib Timer");
        List<Map<String, Object>> matched = allTools().stream()
                .filter(t -> leased.contains(t.get("name")))
                .toList();
        assertThat(matched).hasSize(leased.size());
        for (Map<String, Object> tool : matched) {
            assertThat(tool.get("available")).as("%s must stay withheld", tool.get("name")).isEqualTo(false);
            assertThat(tool.get("maturity")).as("%s is blocked, not merely unbuilt", tool.get("name"))
                    .isEqualTo("Blocked");
        }
    }

    @Test
    void theWiredToolsAreTheOnesWithRealRoutes() {
        Map<String, String> expected = Map.of(
                "Partograph", "/internal/v1/maternity/partograph/sessions",
                "Growth Charts", "/internal/v1/growth",
                "Immunization Schedule", "/internal/v1/immunizations");
        List<Map<String, Object>> available = allTools().stream()
                .filter(t -> Boolean.TRUE.equals(t.get("available")))
                .toList();
        assertThat(available).hasSize(expected.size());
        for (Map<String, Object> tool : available) {
            assertThat(expected).containsEntry((String) tool.get("name"), (String) tool.get("route"));
        }
    }

    @Test
    void metaCountsDoNotOverstateWhatWorks() {
        ResponseEntity<Map<String, Object>> response = CONTROLLER.getSpecialtyWorkspaces();
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        long available = allTools().stream().filter(t -> Boolean.TRUE.equals(t.get("available"))).count();
        assertThat(meta.get("available_tool_count")).isEqualTo(available);
        assertThat(meta.get("tool_count")).isEqualTo((long) allTools().size());
        assertThat((Long) meta.get("available_tool_count"))
                .isLessThan((Long) meta.get("tool_count"));
    }
}
