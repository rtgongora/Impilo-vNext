package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Allowlist-based direct-identifier stripper (design principle 2).
 *
 * <p>Fail-closed in both directions:</p>
 * <ul>
 *   <li>Any column <em>not</em> on the dataset's allowlist is dropped — an
 *       empty/absent allowlist means every column is dropped.</li>
 *   <li>Identifier-named columns (name / phone / address / email / national
 *       ID / health ID / CPID variants) are dropped <em>even when
 *       allowlisted</em> — a mis-configured allowlist can never smuggle a
 *       direct identifier into a dataset.</li>
 * </ul>
 *
 * <p>Column names are normalised (lower-cased, separators removed) before
 * matching, so {@code healthId}, {@code health_id} and {@code HEALTH-ID} are
 * all treated identically.</p>
 */
@Component
public class DirectIdentifierStripper {

    /** Normalised name fragments that mark a column as a direct identifier. */
    private static final Set<String> BANNED_FRAGMENTS = Set.of(
            "name", "phone", "msisdn", "mobile", "address", "email",
            "passport", "cpid", "healthid", "nationalid", "birthcertificate");

    /** Normalised names that are direct identifiers on exact match. */
    private static final Set<String> BANNED_EXACT = Set.of("nid", "id", "ssn", "contact");

    /**
     * Strip a row down to allowlisted, non-identifier columns.
     * Returns a new mutable map; the input row is not modified.
     */
    public Map<String, Object> strip(Map<String, Object> row, Set<String> allowlist) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (row == null) {
            return out;
        }
        Set<String> normalisedAllowlist = normalise(allowlist);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String normalised = normalise(entry.getKey());
            if (isDirectIdentifier(entry.getKey())) {
                continue;   // banned even if allowlisted — fail closed
            }
            if (!normalisedAllowlist.contains(normalised)) {
                continue;   // not allowlisted — dropped
            }
            out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    /** True when the column name identifies a person directly (any naming variant). */
    public boolean isDirectIdentifier(String columnName) {
        String normalised = normalise(columnName);
        if (normalised.isEmpty()) {
            return true;    // unnameable columns are dropped, fail closed
        }
        if (BANNED_EXACT.contains(normalised)) {
            return true;
        }
        for (String fragment : BANNED_FRAGMENTS) {
            if (normalised.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalise(Set<String> names) {
        if (names == null) {
            return Set.of();
        }
        return names.stream()
                .map(DirectIdentifierStripper::normalise)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalise(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    }
}
