package zw.gov.mohcc.impilo.experience.staffing;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the display name and contact number of rostered staff, as a composition — never a copy.
 *
 * <p><b>Why this lives in the BFF.</b> {@code vsh_workforce_profile} deliberately holds no name and
 * no telephone number: person identity is vito's and provider identity is varapi's. Rather than
 * denormalise names into the rostering service (a second identity store, drifting the moment
 * somebody marries or changes number), the rota is composed here — vashandi says which profiles are
 * rostered, vashandi maps those profiles to Health IDs, and varapi says who those Health IDs are.</p>
 *
 * <p><b>Why the name comes from varapi and not vito.</b> Vito's unmasked person read is a
 * break-glass surface: it demands an access reason and writes a
 * {@code CLIENT_RECORD_BREAK_GLASS_ACCESS} audit event per open. Drawing a rota is not a
 * break-glass purpose, and vito's non-break-glass search returns deliberately masked names
 * ({@code "Te***"}) — worse on an on-call card than the worker reference it would replace. Varapi
 * is the registry of professional identity, which is exactly what a rota renders. Somebody rostered
 * with no varapi record therefore resolves to no name, and the surface says so.</p>
 *
 * <p><b>The failure mode that matters.</b> "The registry says this person has no name on file" and
 * "the registry did not answer" must not look alike, because the second one silently degrades an
 * on-call board to bare references and nobody notices the registries are down. So resolution
 * carries its own state ({@link Resolution#status()}), and the caller surfaces it.</p>
 */
@Component
public class StaffIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(StaffIdentityResolver.class);

    /** Every id asked about was answered by the registries (a "no such provider" is an answer). */
    public static final String STATUS_LIVE = "LIVE";
    /** A registry did not answer. Names are absent because resolution failed, not because none exist. */
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    /** Nothing to resolve — no rostered people on this screen. */
    public static final String STATUS_NOT_APPLICABLE = "NOT_APPLICABLE";

    private final VashandiServiceClient vashandiClient;
    private final VarapiServiceClient varapiClient;

    public StaffIdentityResolver(VashandiServiceClient vashandiClient, VarapiServiceClient varapiClient) {
        this.vashandiClient = vashandiClient;
        this.varapiClient = varapiClient;
    }

    /** What the experience layer knows about one rostered person, beyond their reference. */
    public record StaffIdentity(String displayName, String phone, String profession, String cadre) {}

    /**
     * The resolution of a screen's worth of profiles: what was found, and whether the finding can
     * be trusted to mean anything.
     */
    public record Resolution(String status, Map<String, StaffIdentity> byProfileId) {

        public StaffIdentity get(String profileId) {
            return profileId == null ? null : byProfileId.get(profileId);
        }

        public boolean isLive() {
            return STATUS_LIVE.equals(status);
        }

        static Resolution notApplicable() {
            return new Resolution(STATUS_NOT_APPLICABLE, Map.of());
        }

        static Resolution unavailable() {
            return new Resolution(STATUS_UNAVAILABLE, Map.of());
        }
    }

    /**
     * Resolve display facts for the given workforce profile ids.
     *
     * <p>Never throws: a rota that cannot resolve names is still a rota worth rendering, and the
     * references are already in hand. It reports {@link #STATUS_UNAVAILABLE} instead, so the screen
     * can say the names are missing rather than implying nobody has one.</p>
     */
    public Resolution resolve(Set<String> workforceProfileIds) {
        if (workforceProfileIds == null || workforceProfileIds.isEmpty()) {
            return Resolution.notApplicable();
        }

        Map<String, String> healthIdByProfile;
        try {
            healthIdByProfile = healthIdsByProfile(workforceProfileIds);
        } catch (Exception e) {
            log.warn("STAFF_IDENTITY_RESOLUTION_UNAVAILABLE vashandi identity-refs failed: {}", e.getMessage());
            return Resolution.unavailable();
        }
        if (healthIdByProfile.isEmpty()) {
            // Vashandi answered and none of these profiles carries a person anchor. That is a real
            // finding, not a failure: the references stand and nothing is missing from the screen.
            return new Resolution(STATUS_LIVE, Map.of());
        }

        Map<String, StaffIdentity> byHealthId;
        try {
            byHealthId = displayFactsByHealthId(new ArrayList<>(new LinkedHashSet<>(healthIdByProfile.values())));
        } catch (Exception e) {
            log.warn("STAFF_IDENTITY_RESOLUTION_UNAVAILABLE varapi resolve-display failed: {}", e.getMessage());
            return Resolution.unavailable();
        }

        Map<String, StaffIdentity> byProfileId = new LinkedHashMap<>();
        healthIdByProfile.forEach((profileId, healthId) -> {
            StaffIdentity identity = byHealthId.get(healthId);
            if (identity != null) {
                byProfileId.put(profileId, identity);
            }
        });
        return new Resolution(STATUS_LIVE, byProfileId);
    }

    private Map<String, String> healthIdsByProfile(Set<String> profileIds) {
        JsonNode refs = vashandiClient.getWorkforceIdentityRefs(String.join(",", profileIds));
        Map<String, String> byProfile = new LinkedHashMap<>();
        if (refs == null || !refs.isArray()) {
            // A null body is not an empty rota: vashandi returns a list, so no list means no answer.
            throw new IllegalStateException("vashandi returned no identity-ref list");
        }
        for (JsonNode row : refs) {
            String profileId = text(row, "workforce_profile_id");
            String healthId = text(row, "health_id");
            if (profileId != null && healthId != null) {
                byProfile.put(profileId, healthId);
            }
        }
        return byProfile;
    }

    private Map<String, StaffIdentity> displayFactsByHealthId(List<String> healthIds) {
        JsonNode resolved = varapiClient.resolveDisplayFacts(healthIds);
        if (resolved == null || !resolved.isArray()) {
            throw new IllegalStateException("varapi returned no display-facts list");
        }
        Map<String, StaffIdentity> byHealthId = new LinkedHashMap<>();
        for (JsonNode row : resolved) {
            if (!row.path("resolved").asBoolean(false)) {
                continue;
            }
            String healthId = text(row, "healthId");
            String displayName = text(row, "displayName");
            if (healthId == null || displayName == null) {
                // A registry row with no name resolves to no name: the reference is more useful
                // than a blank, and more honest than a title on its own.
                continue;
            }
            byHealthId.put(healthId, new StaffIdentity(
                    displayName, text(row, "phone"), text(row, "profession"), text(row, "cadre")));
        }
        return byHealthId;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String s = value.asText();
        return s == null || s.isBlank() ? null : s;
    }
}
