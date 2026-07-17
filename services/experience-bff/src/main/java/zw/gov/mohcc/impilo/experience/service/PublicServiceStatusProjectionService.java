package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Honest projection of the observability service-heartbeat feed into a small set of
 * citizen-facing service groups (gateway ADR — public service-status read).
 *
 * <p>This is the doctrine crux of the read lane: the board must never fabricate a green
 * light. A citizen group is reported {@code OPERATIONAL} only when <b>every</b> internal
 * member service it maps to is present in the feed AND reporting {@code UP}. A member that
 * is absent from the feed contributes {@code UNKNOWN} (not operational); a stale member
 * degrades the group; and a group with no monitored members at all is {@code UNKNOWN},
 * {@code monitored:false} so the UI falls back to its indicative static label rather than
 * claiming health we cannot prove.</p>
 *
 * <p>The internal service-name taxonomy lives here in a {@code service/} class — not on any
 * public controller surface — so internal names never leak onto a citizen surface
 * (config/gateway-public-naming.yml). Member tokens are matched case-insensitively by
 * substring against the feed {@code serviceName} (so {@code "varapi"} matches
 * {@code "varapi-service"}).</p>
 */
@Service
public class PublicServiceStatusProjectionService {

    /** Coarse group state vocabulary shared with the shell. */
    static final String OPERATIONAL = "OPERATIONAL";
    static final String DEGRADED = "DEGRADED";
    static final String PARTIAL = "PARTIAL";
    static final String UNAVAILABLE = "UNAVAILABLE";
    static final String UNKNOWN = "UNKNOWN";

    /**
     * Citizen groups, in display order. Each maps to one or more internal heartbeat
     * service-name tokens (case-insensitive substring). {@code experience-bff} is a member
     * of every group because the front door itself gates access to the capability.
     */
    private static final List<Group> GROUPS = List.of(
            new Group("Find care & facility directory",
                    "Search facilities and find care near you",
                    List.of("tuso", "ndila", "varapi", "experience-bff")),
            new Group("Emergency help",
                    "Emergency SOS and urgent help",
                    List.of("daidzai", "experience-bff")),
            new Group("Sign in & accounts",
                    "Sign in, register, and manage your account",
                    List.of("vito", "tshepo", "experience-bff")),
            new Group("Health records & results",
                    "View your health records and test results",
                    List.of("butano", "experience-bff")),
            new Group("Payments & health cover",
                    "Health bills, wallet, payments, and cover",
                    List.of("costa", "mushe", "coverage", "experience-bff")),
            new Group("Report a problem & feedback",
                    "Report a problem and send feedback",
                    List.of("rito", "guidance", "participation", "experience-bff")));

    private record Group(String label, String description, List<String> memberTokens) {}

    /**
     * Project the observability status feed. When {@code feed} is {@code null} (observability
     * unreachable) every group is reported {@code UNKNOWN}/{@code monitored:false} and
     * {@code live:false} — the UI falls back to its indicative static board. A {@code 200}
     * with honest gaps, never a fabricated green.
     *
     * @param feed the upstream {@code /v1/public/ops/status} body, or {@code null} on failure
     * @return response body: {@code { updatedAt, live, groups:[{group,description,state,monitored}] }}
     */
    public Map<String, Object> project(JsonNode feed) {
        boolean live = feed != null;

        // Flatten the feed to serviceName(lowercase) -> status(uppercase). Absent = not present.
        List<String[]> feedEntries = new ArrayList<>();
        String updatedAt = null;
        if (live) {
            updatedAt = feed.path("generatedAt").asText(null);
            JsonNode services = feed.path("services");
            if (services.isArray()) {
                for (JsonNode s : services) {
                    String name = s.path("serviceName").asText("");
                    if (name.isBlank()) {
                        continue;
                    }
                    String status = s.path("status").asText("").trim().toUpperCase(Locale.ROOT);
                    feedEntries.add(new String[]{name.toLowerCase(Locale.ROOT), status});
                }
            }
        }
        if (updatedAt == null || updatedAt.isBlank()) {
            updatedAt = Instant.now().toString();
        }

        List<Map<String, Object>> groups = new ArrayList<>(GROUPS.size());
        for (Group g : GROUPS) {
            groups.add(projectGroup(g, feedEntries, live));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("updatedAt", updatedAt);
        out.put("live", live);
        out.put("groups", groups);
        return out;
    }

    private Map<String, Object> projectGroup(Group g, List<String[]> feedEntries, boolean live) {
        int up = 0;
        int stale = 0;
        int down = 0;
        int absent = 0;

        if (live) {
            for (String token : g.memberTokens()) {
                String worst = worstStatusForToken(token, feedEntries);
                if (worst == null) {
                    absent++;
                } else if ("UP".equals(worst)) {
                    up++;
                } else if ("STALE".equals(worst)) {
                    stale++;
                } else {
                    // Any present, explicitly not-UP/not-STALE status is treated as down-ish.
                    down++;
                }
            }
        } else {
            absent = g.memberTokens().size();
        }

        int present = up + stale + down;
        boolean monitored = present > 0;
        String state;
        if (!monitored) {
            state = UNKNOWN;                                  // nothing to prove health with
        } else if (down > 0 && up == 0 && stale == 0) {
            state = UNAVAILABLE;                              // every present member is down-ish
        } else if (stale > 0 || down > 0) {
            state = DEGRADED;                                 // some impaired, not all down
        } else if (absent == 0) {
            state = OPERATIONAL;                              // ALL members present AND up
        } else {
            state = PARTIAL;                                  // present members up, but not all monitored
        }

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("group", g.label());
        node.put("description", g.description());
        node.put("state", state);
        node.put("monitored", monitored);
        return node;
    }

    /**
     * Worst status among all feed entries whose serviceName contains {@code token}, or
     * {@code null} when the token matches nothing in the feed (member absent). Ordering
     * (worst first): down-ish > STALE > UP.
     */
    private String worstStatusForToken(String token, List<String[]> feedEntries) {
        String worst = null;
        for (String[] entry : feedEntries) {
            if (!entry[0].contains(token)) {
                continue;
            }
            String status = entry[1];
            worst = worse(worst, status);
        }
        return worst;
    }

    private String worse(String current, String candidate) {
        if (current == null) {
            return candidate;
        }
        return rank(candidate) > rank(current) ? candidate : current;
    }

    private int rank(String status) {
        if ("UP".equals(status)) {
            return 0;
        }
        if ("STALE".equals(status)) {
            return 1;
        }
        return 2; // down-ish / unrecognized present status
    }
}
