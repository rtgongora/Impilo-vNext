package zw.gov.mohcc.impilo.shared.visibility;

import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataSensitivityClass;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataVisibilityTier;

import java.util.List;
import java.util.function.Function;

/**
 * Guards content classified {@link DataSensitivityClass#SPECIALLY_PROTECTED} — restricted
 * adolescent and safeguarding data, sexual and reproductive health, HIV, mental health.
 *
 * <p>The PDP decides <em>whether this requester is entitled</em> to protected content and says so in
 * the {@link VisibilityProfile} it issues. A system of record decides <em>which of its records</em>
 * carry the class. Both halves are needed: the PDP sees a URL, never a record, so it cannot know
 * that row 7 of a collection is a safeguarding note; the service knows that but must not invent its
 * own access rule. This guard is where a service consumes the PDP's answer.</p>
 *
 * <h2>This guard fails CLOSED, unlike its siblings</h2>
 * <p>{@link AggregateVisibilityGuard} and {@link ClinicalVisibilityGuard} treat a missing profile as
 * permissive, so services not yet wired to the obligation headers keep working. This guard does the
 * opposite: no profile means the PDP never said yes, and for the highest confidentiality class
 * silence must not mean disclosure. A service that returns protected records because a header was
 * absent is exactly the false assurance this whole seam exists to remove.</p>
 */
public final class SpeciallyProtectedVisibilityGuard {

    private SpeciallyProtectedVisibilityGuard() {
    }

    /**
     * Whether the requester behind {@code profile} may receive specially-protected content.
     * Fails closed on a null profile or an unparseable tier.
     */
    public static boolean allowsProtectedContent(VisibilityProfile profile) {
        if (profile == null || profile.visibilityTier() == null) {
            return false;
        }
        return DataVisibilityTier.fromString(profile.visibilityTier()).allowsSpeciallyProtected();
    }

    /** As {@link #allowsProtectedContent(VisibilityProfile)}, against the current request context. */
    public static boolean allowsProtectedContent() {
        return VisibilityContextHolder.current()
                .map(SpeciallyProtectedVisibilityGuard::allowsProtectedContent)
                .orElse(false);
    }

    /**
     * Whether a single record must be withheld: it carries the protected class and this requester is
     * not entitled to it. Callers should answer with the same response they would give for a record
     * that does not exist — a 403 distinguishable from a 404 tells the guardian that the confidential
     * record is there, which is most of what confidentiality was protecting.
     *
     * @param recordSensitivityClass the record's stamped {@link DataSensitivityClass} name; a null or
     *        unrecognised value is treated as not protected, since an unstamped record is ordinary
     *        clinical data and must not be withheld from the people entitled to see it.
     */
    public static boolean withholds(String recordSensitivityClass, VisibilityProfile profile) {
        return isProtected(recordSensitivityClass) && !allowsProtectedContent(profile);
    }

    /** As {@link #withholds(String, VisibilityProfile)}, against the current request context. */
    public static boolean withholds(String recordSensitivityClass) {
        return isProtected(recordSensitivityClass) && !allowsProtectedContent();
    }

    /**
     * Remove protected rows from a collection when the requester is not entitled to them, leaving
     * ordinary rows intact. This is the mixed-collection case the PDP cannot decide: a request for a
     * person's encounters is allowed, but three of the forty encounters are confidential.
     *
     * <p>Returns the list unchanged when the requester IS entitled, and never returns null.</p>
     *
     * @param sensitivityOf reads a row's stamped sensitivity class name
     */
    public static <T> List<T> filterProtected(List<T> rows,
                                              Function<T, String> sensitivityOf,
                                              VisibilityProfile profile) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (allowsProtectedContent(profile)) {
            return rows;
        }
        return rows.stream()
                .filter(row -> !isProtected(sensitivityOf.apply(row)))
                .toList();
    }

    /** As {@link #filterProtected(List, Function, VisibilityProfile)}, against the current context. */
    public static <T> List<T> filterProtected(List<T> rows, Function<T, String> sensitivityOf) {
        return filterProtected(rows, sensitivityOf,
                VisibilityContextHolder.current().orElse(null));
    }

    /** Whether a stamped sensitivity class name is the specially-protected class. */
    public static boolean isProtected(String recordSensitivityClass) {
        if (recordSensitivityClass == null || recordSensitivityClass.isBlank()) {
            return false;
        }
        // DataSensitivityClass.fromString falls back to NON_SENSITIVE_OPERATIONAL on junk, which is
        // the safe direction here: an unreadable stamp must not silently withhold ordinary care.
        return DataSensitivityClass.fromString(recordSensitivityClass)
                == DataSensitivityClass.SPECIALLY_PROTECTED;
    }
}
