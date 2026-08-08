package zw.gov.mohcc.impilo.zibo.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import zw.gov.mohcc.impilo.zibo.domain.VersionScheme;

/**
 * Turns a publisher's version string into something orderable, without rewriting it.
 *
 * <p>ZIBO decided which version of an artifact was current by <em>clock</em> —
 * {@code max(comparing(publishedAt))} in {@code ValidationEngine} and
 * {@code MedicineRegistryService}, {@code createdAt DESC} in
 * {@code ArtifactRepository.findAllVersions}. Publishing a 1.0.1 correction after 1.1.0 therefore
 * silently started serving the older content, and for external terminologies the clock and the
 * version agree only by coincidence.</p>
 *
 * <p><b>The publisher's string is never modified.</b> LOINC's {@code 2.82} stays {@code 2.82} and
 * ATC's {@code 2026.01} stays {@code 2026.01}. Historical clinical records carry the version they
 * were captured against, so rewriting it would orphan them. This class only derives a parallel
 * sort key.</p>
 *
 * <p>The same rules are applied once, in SQL, by {@code V400} to backfill existing rows. This class
 * is the ongoing implementation; {@code VersionOrderingTest} pins both against the same corpus of
 * real version strings from the estate and from the publishers this programme will load.</p>
 */
public final class VersionOrdering {

    private VersionOrdering() {}

    /** {@code 2026.01}, {@code 2026-01}, {@code 2025.1} — a release date written as a version. */
    private static final Pattern DATE = Pattern.compile("^([0-9]{4})[.-]([0-9]{1,2})$");

    /** {@code 1.1.0} and {@code 2.82} alike. The third part is optional. */
    private static final Pattern NUMERIC = Pattern.compile("^([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?");

    private static final Pattern INTEGER = Pattern.compile("^([0-9]+)$");

    private static final int PAD = 6;

    /**
     * Classifies a version string.
     *
     * <p>Order matters and is load-bearing. {@code DATE} is tested first because {@code 2025.1} and
     * {@code 2.82} are both "digits dot digits" and only the four-digit leading year separates a
     * release date from LOINC's two-part version. Testing numerically first would classify every
     * ATC and EDLIZ release as a version number and sort {@code 2026.01} below {@code 2025.1}.</p>
     */
    public static VersionScheme detect(String version) {
        if (version == null || version.isBlank()) {
            return VersionScheme.PUBLISHER_DEFINED;
        }
        String v = version.trim();
        if (DATE.matcher(v).matches()) {
            return VersionScheme.DATE;
        }
        if (NUMERIC.matcher(v).find() && NUMERIC.matcher(v).lookingAt()) {
            // Two-part is deliberately included. LOINC's minor part is a sequential integer, and as
            // raw strings "2.9" sorts above "2.82", which is backwards.
            return VersionScheme.SEMVER;
        }
        if (INTEGER.matcher(v).matches()) {
            return VersionScheme.INTEGER;
        }
        return VersionScheme.PUBLISHER_DEFINED;
    }

    /**
     * Derives a lexicographically comparable key, so a plain {@code ORDER BY} is correct.
     *
     * <p>Zero-padded to six digits per part: SemVer {@code 1.10.0} must sort above {@code 1.9.0},
     * which a naive string compare gets backwards.</p>
     *
     * <p>{@link VersionScheme#PUBLISHER_DEFINED} returns the raw string. {@code 10-2025} could be
     * release 10 of 2025 or October 2025; sorting it as text is honest about not knowing, where
     * guessing an order would be confidently wrong.</p>
     */
    public static String sortKey(String version, VersionScheme scheme) {
        if (version == null) {
            return "";
        }
        String v = version.trim();
        return switch (scheme) {
            case DATE -> {
                Matcher m = DATE.matcher(v);
                yield m.matches() ? join(m.group(1), m.group(2), "0") : v;
            }
            case SEMVER -> {
                Matcher m = NUMERIC.matcher(v);
                yield m.lookingAt() ? join(m.group(1), m.group(2), m.group(3)) : v;
            }
            case INTEGER -> join(v, "0", "0");
            case PUBLISHER_DEFINED -> v;
        };
    }

    /** Convenience: classify and derive in one step. */
    public static String sortKey(String version) {
        return sortKey(version, detect(version));
    }

    private static String join(String major, String minor, String patch) {
        return pad(major) + "." + pad(minor) + "." + pad(patch);
    }

    private static String pad(String part) {
        String digits = (part == null || part.isBlank()) ? "0" : part;
        if (digits.length() >= PAD) {
            return digits;
        }
        return "0".repeat(PAD - digits.length()) + digits;
    }
}
