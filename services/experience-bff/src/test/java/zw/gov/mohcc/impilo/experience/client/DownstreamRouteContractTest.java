package zw.gov.mohcc.impilo.experience.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every path the BFF calls must be a path the downstream service actually serves.
 *
 * <p><strong>Why this exists.</strong> The estate's most persistent clinical defect is not a
 * swallowed error — it is a wrong route. The BFF calls an endpoint nobody serves, the downstream
 * answers 404, and the BFF renders that as an empty list. Every layer reports success and the
 * clinical surface says "none". It has now happened five times that we know of: paediatric growth,
 * immunisation and maternity (all three found by that pack), the adult problem list
 * ({@code /v1/conditions} against a service that serves {@code /v1/problems}), and the shift /
 * on-call / ward vertical found by this test on its first run.</p>
 *
 * <p>The query-honesty guard in {@code ui/one-ui-shell} closes the sibling hole — a client
 * discarding {@code isError}. It cannot close this one, because a wrong route that the BFF has
 * turned into an empty 200 has no error to discard. The two guards are complements.</p>
 *
 * <p><strong>What it checks.</strong> Path literals in each {@code *Client} are reduced to their
 * first two segments ({@code /v1/problems}) and matched against the {@code @RequestMapping} and
 * method-mapping paths declared anywhere in the target service. Two segments rather than the full
 * path because clients build paths by concatenation — {@code baseUrl + "/v1/problems/" + id +
 * "/resolve"} — so the full path is not a literal and cannot be resolved statically. Two segments
 * is enough: every instance of this defect so far has been wrong at the resource name, not at a
 * sub-path.</p>
 *
 * <p><strong>It ratchets.</strong> Known violations live in {@link #BASELINE} and are reported but
 * do not fail. A new one fails the build. Fix a baseline entry and delete its line — the list may
 * only shrink. A guard that cannot be landed today is not a guard.</p>
 */
class DownstreamRouteContractTest {

    private static final Path SERVICES = Path.of("..");
    private static final Path CLIENT_DIR =
            Path.of("src/main/java/zw/gov/mohcc/impilo/experience/client");

    /** Path literals worth checking — the prefixes services actually route on. */
    private static final Pattern PATH_LITERAL =
            Pattern.compile("\"(/(?:v\\d+|internal|fhir)/[a-zA-Z0-9\\-_/]*)");

    private static final Pattern CLASS_MAPPING =
            Pattern.compile("@RequestMapping\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");
    private static final Pattern METHOD_MAPPING =
            Pattern.compile("@(?:Get|Post|Put|Patch|Delete)Mapping\\(\\s*(?:value\\s*=\\s*)?\"(/[^\"]*)\"");

    /**
     * Known-unserved prefixes, recorded rather than fixed here because they belong to other lanes.
     * Each line is a real defect, not an exemption.
     *
     * <ul>
     *   <li><b>Tuso shifts / staffing / wards</b> — tuso-service serves shifts at
     *       {@code /v1/internal/facilities/{facilityId}/...}; nothing in the estate serves
     *       {@code /v1/staffing} or {@code /v1/wards} at all. Shift start/end, the on-call roster,
     *       swaps and the ward list are dead. Reported to the workforce/facility lane.</li>
     *   <li><b>FHIR gateway</b> — proxied to HAPI rather than declared as Spring routes, so the
     *       scanner cannot see them. Verify before removing this line.</li>
     *   <li><b>Nhume {@code /internal/v1}</b> — a bare prefix from a concatenated path; the
     *       scanner cannot resolve the resource segment.</li>
     * </ul>
     */
    private static final Set<String> BASELINE = Set.of(
            // ── Workforce / facility lane ────────────────────────────────────────
            // tuso-service serves shifts at /v1/internal/facilities/{facilityId}/…; nothing in the
            // estate serves /v1/staffing or /v1/wards at all. Shift start/end, the on-call roster,
            // swaps and the ward list are dead.
            "TusoServiceClient -> /v1/shifts",
            "TusoServiceClient -> /v1/staffing",
            "TusoServiceClient -> /v1/wards",

            // ── Inpatient lane ───────────────────────────────────────────────────
            // These are inpatient concepts called against pct-service, which serves none of them;
            // inpatient-service routes under /internal/v1/… so the prefix is wrong twice over.
            // Vitals is the alarming one — it is as core as a clinical read gets.
            "PctServiceClient -> /v1/vitals",
            "PctServiceClient -> /v1/ews",
            "PctServiceClient -> /v1/ward-rounds",
            "PctServiceClient -> /v1/apgar",
            "PctServiceClient -> /v1/fluid-balance",
            "PctServiceClient -> /v1/transfers",
            "PctServiceClient -> /v1/discharge-clearances",
            "PctServiceClient -> /v1/records",

            // ── Adult Medicine lane (mine) ───────────────────────────────────────
            // The structured-history vertical: social, family, functional, procedures and advance
            // directives, calling pct paths that do not exist and falling through to a demo
            // fixture. W2 builds the system of record; this line goes when it lands.
            "PctServiceClient -> /v1/ehr",

            // ── Not resolvable by a static scan ──────────────────────────────────
            // Proxied to HAPI rather than declared as Spring routes.
            "FhirGatewayServiceClient -> /fhir/",
            "FhirGatewayServiceClient -> /fhir/metadata",
            // A bare prefix from a concatenated path; the resource segment is not a literal.
            "NhumeServiceClient -> /internal/v1");

    @Test
    void everyPathTheBffCallsIsServedByItsDownstreamService() throws IOException {
        List<String> violations = new ArrayList<>();
        int clientsChecked = 0;
        int literalsChecked = 0;

        try (Stream<Path> clients = Files.list(CLIENT_DIR)) {
            for (Path client : clients.filter(p -> p.toString().endsWith("Client.java")).toList()) {
                String clientName = client.getFileName().toString().replace(".java", "");
                Path service = serviceDirFor(clientName);
                if (service == null) {
                    // No conventional service directory. Not a failure — some clients target
                    // external systems (Keycloak) or services named differently.
                    continue;
                }
                clientsChecked++;
                Set<String> declared = declaredRoutes(service);
                String source = Files.readString(client);

                Matcher m = PATH_LITERAL.matcher(source);
                while (m.find()) {
                    literalsChecked++;
                    String prefix = firstTwoSegments(m.group(1));
                    if (!isServed(prefix, declared)) {
                        violations.add(clientName + " -> " + prefix);
                    }
                }
            }
        }

        Set<String> distinct = new TreeSet<>(violations);
        Set<String> fresh = new LinkedHashSet<>(distinct);
        fresh.removeAll(BASELINE);

        // Sanity: a scanner that resolves nothing would pass silently and prove nothing.
        assertThat(clientsChecked)
                .withFailMessage("no clients resolved to a service — the naming convention has changed")
                .isGreaterThan(20);
        assertThat(literalsChecked)
                .withFailMessage("no path literals matched — the pattern has stopped working")
                .isGreaterThan(200);

        assertThat(fresh)
                .withFailMessage("""
                        The BFF calls %d path(s) no downstream service serves.

                        This is the estate's most persistent clinical defect: the downstream answers
                        404, the BFF renders it as an empty list, and the clinical surface says
                        "none" while every layer reports success.

                        Fix the client to call the path the service actually serves. Only add to
                        BASELINE if the route genuinely exists but is invisible to a static scan,
                        and say why.

                          %s""", fresh.size(), String.join("\n  ", fresh))
                .isEmpty();
    }

    /** The baseline is a debt register: entries must be paid down, never quietly kept alive. */
    @Test
    void theBaselineOnlyContainsViolationsThatStillExist() throws IOException {
        Set<String> current = new HashSet<>();
        try (Stream<Path> clients = Files.list(CLIENT_DIR)) {
            for (Path client : clients.filter(p -> p.toString().endsWith("Client.java")).toList()) {
                String clientName = client.getFileName().toString().replace(".java", "");
                Path service = serviceDirFor(clientName);
                if (service == null) continue;
                Set<String> declared = declaredRoutes(service);
                Matcher m = PATH_LITERAL.matcher(Files.readString(client));
                while (m.find()) {
                    String prefix = firstTwoSegments(m.group(1));
                    if (!isServed(prefix, declared)) current.add(clientName + " -> " + prefix);
                }
            }
        }
        Set<String> stale = new TreeSet<>(BASELINE);
        stale.removeAll(current);
        assertThat(stale)
                .withFailMessage("These baseline entries are fixed — delete them so the list keeps "
                                 + "shrinking:%n  %s", String.join("\n  ", stale))
                .isEmpty();
    }

    // ── Mechanics ───────────────────────────────────────────────────────────────

    private static Path serviceDirFor(String clientName) {
        String stem = clientName.replace("ServiceClient", "").replace("Client", "");
        String kebab = stem.replaceAll("(?<!^)(?=[A-Z])", "-").toLowerCase(Locale.ROOT);
        for (String candidate : List.of(kebab + "-service", kebab)) {
            Path p = SERVICES.resolve(candidate);
            if (Files.isDirectory(p)) return p;
        }
        return null;
    }

    /**
     * The absolute routes a service serves.
     *
     * <p>Method mappings are <strong>relative to the class mapping</strong> and must be composed,
     * not collected alongside it. Treating both as absolute was this test's own first bug: a
     * controller declaring {@code @RequestMapping("/v1")} — pct-service has one — made every
     * {@code /v1/*} prefix appear served, and the guard passed while pointed at the exact defect
     * it was written for. Found by mutation, not by reading.</p>
     */
    private static Set<String> declaredRoutes(Path service) throws IOException {
        Set<String> routes = new HashSet<>();
        try (Stream<Path> files = Files.walk(service)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/")).toList()) {
                String source = Files.readString(f);

                Matcher classMatcher = CLASS_MAPPING.matcher(source);
                String classPath = classMatcher.find() ? classMatcher.group(1) : "";
                if (!classPath.isEmpty()) {
                    routes.add(classPath);
                }

                Matcher methodMatcher = METHOD_MAPPING.matcher(source);
                boolean anyMethodPath = false;
                while (methodMatcher.find()) {
                    anyMethodPath = true;
                    routes.add(join(classPath, methodMatcher.group(1)));
                }
                // A controller whose methods carry no path serves the class path itself.
                if (!anyMethodPath && !classPath.isEmpty()) {
                    routes.add(classPath);
                }
            }
        }
        return routes;
    }

    private static String join(String classPath, String methodPath) {
        if (classPath.isEmpty()) return methodPath;
        return stripTrailing(classPath) + methodPath;
    }

    private static String firstTwoSegments(String path) {
        String[] parts = path.split("/");
        return parts.length >= 3 ? "/" + parts[1] + "/" + parts[2] : path;
    }

    /**
     * A prefix is served when some declared route is the prefix itself or lives beneath it.
     *
     * <p>An <em>ancestor</em> route deliberately does not count. {@code /v1} does not serve
     * {@code /v1/conditions} — that is precisely the mistake being hunted, and accepting ancestors
     * makes the guard match everything.</p>
     */
    private static boolean isServed(String prefix, Set<String> declared) {
        String needle = stripTrailing(prefix);
        for (String route : declared) {
            if (route == null || route.isBlank()) continue;
            String candidate = stripTrailing(route);
            if (candidate.equals(needle) || candidate.startsWith(needle + "/")) return true;
        }
        return false;
    }

    private static String stripTrailing(String route) {
        return route.endsWith("/") ? route.substring(0, route.length() - 1) : route;
    }
}
