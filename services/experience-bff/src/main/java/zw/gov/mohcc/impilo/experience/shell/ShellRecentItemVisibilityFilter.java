package zw.gov.mohcc.impilo.experience.shell;

import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Filters persisted shell recent items using Tshepo-derived {@link VisibilityProfile}
 * (aggregate-only / clinical access signals mirrored from gateway headers).
 */
public final class ShellRecentItemVisibilityFilter {

    private ShellRecentItemVisibilityFilter() {
    }

    public static List<Map<String, Object>> filter(List<Map<String, Object>> items, VisibilityProfile profile) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (profile == null) {
            return List.copyOf(items);
        }
        boolean aggregateOnly = Boolean.TRUE.equals(profile.aggregateOnly());
        boolean clinicalBlocked = profile.clinicalAccess() == null
                || "NONE".equalsIgnoreCase(String.valueOf(profile.clinicalAccess()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : items) {
            if (aggregateOnly && isClinicalRow(row)) {
                continue;
            }
            if (clinicalBlocked && isClinicalRow(row)) {
                continue;
            }
            if (!Boolean.TRUE.equals(profile.drillDownAllowed()) && isPatientScopedHref(row)) {
                continue;
            }
            out.add(row);
        }
        return out;
    }

    private static boolean isClinicalRow(Map<String, Object> row) {
        Object sens = row.get("sensitivity");
        if (sens != null && "clinical".equalsIgnoreCase(String.valueOf(sens))) {
            return true;
        }
        Object kind = row.get("kind");
        if (kind != null && "patient_chart".equalsIgnoreCase(String.valueOf(kind))) {
            return true;
        }
        return false;
    }

    private static boolean isPatientScopedHref(Map<String, Object> row) {
        Object href = row.get("href");
        if (!(href instanceof String s)) {
            return false;
        }
        String h = s.toLowerCase(Locale.ROOT);
        return h.startsWith("/ehr/") || h.contains("/patient");
    }
}
