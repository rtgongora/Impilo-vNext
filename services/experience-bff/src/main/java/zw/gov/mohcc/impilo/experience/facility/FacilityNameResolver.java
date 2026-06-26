package zw.gov.mohcc.impilo.experience.facility;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Resolves opaque facility identifiers (numeric TUSO ids, seeded UUIDs/slugs) to display names.
 */
@Service
public class FacilityNameResolver {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final TusoServiceClient tusoServiceClient;

    public FacilityNameResolver(TusoServiceClient tusoServiceClient) {
        this.tusoServiceClient = tusoServiceClient;
    }

    public String resolve(String facilityRef) {
        if (facilityRef == null || facilityRef.isBlank()) {
            return facilityRef;
        }
        String trimmed = facilityRef.trim();

        // Authoritative: resolve a numeric TUSO facility id against the real registry first.
        try {
            long numericId = Long.parseLong(trimmed);
            String fromTuso = resolveFromTuso(numericId);
            if (fromTuso != null && !fromTuso.isBlank()) {
                return fromTuso;
            }
        } catch (NumberFormatException ignored) {
            // not a numeric TUSO id
        }

        // Fallback only: deterministic seeded names for preview/dev UUID/slug registries
        // where TUSO numeric lookup does not apply or is unavailable.
        String seeded = BffSeededFacilities.resolveName(trimmed);
        if (seeded != null && !seeded.isBlank()) {
            return seeded;
        }

        if (UUID_PATTERN.matcher(trimmed).matches()) {
            return humanizeUuid(trimmed);
        }
        return trimmed;
    }

    private String resolveFromTuso(long facilityId) {
        try {
            JsonNode facility = tusoServiceClient.getFacility(facilityId);
            String name = text(facility, "name");
            if (!name.isBlank()) {
                return name;
            }
        } catch (Exception ignored) {
            // fall through to regulatory profile
        }
        try {
            JsonNode profile = tusoServiceClient.getFacilityRegulatoryProfile(String.valueOf(facilityId));
            String masterName = text(profile != null ? profile.path("master") : null, "name");
            if (!masterName.isBlank()) {
                return masterName;
            }
        } catch (Exception ignored) {
            // upstream unavailable or not found
        }
        return null;
    }

    private static String humanizeUuid(String uuid) {
        String shortId = uuid.substring(0, 8).toUpperCase(Locale.ROOT);
        return "Issuance facility · " + shortId;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("").trim();
    }
}
