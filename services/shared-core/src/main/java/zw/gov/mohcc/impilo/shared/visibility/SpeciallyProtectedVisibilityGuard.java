package zw.gov.mohcc.impilo.shared.visibility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.DataSensitivityClass;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Guards content classified {@link DataSensitivityClass#SPECIALLY_PROTECTED} — restricted
 * adolescent and safeguarding data, sexual and reproductive health, HIV, mental health.
 *
 * <p>The PDP decides <em>which confidential categories this requester may receive</em> and says so
 * in the {@link VisibilityProfile#confidentialCategories()} obligation. A system of record decides
 * <em>which of its records</em> carry the class and in <em>which category</em>. Both halves are
 * needed: the PDP sees a URL, never a record, so it cannot know that row 7 of a collection is a
 * safeguarding note; the service knows that but must not invent its own access rule. This guard is
 * where a service consumes the PDP's answer.</p>
 *
 * <p>Grants are <strong>category-scoped</strong>, not a single on/off flag, so a safeguarding lead
 * can hold safeguarding without thereby holding the adolescent's mental-health notes.</p>
 *
 * <h2>Emergency care is never blocked</h2>
 * <p>Under {@code EMERGENCY} or {@code BREAK_GLASS} purpose-of-use the guard waives and logs a loud
 * warning naming the actor and subject, mirroring {@code ClinicalAccessGuard} in pct-service so the
 * two layers behave the same way. Over-restricting kills people too: a teenager arriving unconscious
 * whose HIV status or medication explains the presentation must not be invisible to the clinician
 * treating them. The waiver is detection, not prevention — it relies on the audit being reviewed.</p>
 *
 * <h2>This guard otherwise fails CLOSED, unlike its siblings</h2>
 * <p>{@link AggregateVisibilityGuard} and {@link ClinicalVisibilityGuard} treat a missing profile as
 * permissive, so services not yet wired to the obligation headers keep working. Outside the
 * emergency waiver this guard does the opposite: no profile means the PDP never said yes, and for
 * the highest confidentiality class silence must not mean disclosure.</p>
 */
public final class SpeciallyProtectedVisibilityGuard {

    private static final Logger log = LoggerFactory.getLogger(SpeciallyProtectedVisibilityGuard.class);

    private SpeciallyProtectedVisibilityGuard() {
    }

    /**
     * Whether the requester may receive content in {@code category}. Fails closed on a null profile
     * or an ungranted category. Does not consider the emergency waiver — use
     * {@link #withholds(String, String, VisibilityProfile, TrustContext)} for a disclosure decision.
     */
    public static boolean allowsCategory(String category, VisibilityProfile profile) {
        return profile != null && profile.allowsConfidentialCategory(category);
    }

    /** As {@link #allowsCategory(String, VisibilityProfile)}, against the current request context. */
    public static boolean allowsCategory(String category) {
        return VisibilityContextHolder.current()
                .map(p -> p.allowsConfidentialCategory(category))
                .orElse(false);
    }

    /** Whether any protected content at all is reachable by this requester. */
    public static boolean allowsAnyProtectedContent(VisibilityProfile profile) {
        return profile != null && profile.allowsAnyConfidentialCategory();
    }

    /**
     * Whether a single record must be withheld: it carries the protected class, this requester holds
     * no grant for its category, and no emergency waiver applies.
     *
     * <p>Callers should answer a withheld record the same way they answer one that does not exist —
     * a 403 distinguishable from a 404 tells the guardian that the confidential record is there,
     * which is most of what confidentiality was protecting.</p>
     *
     * @param recordSensitivityClass the record's stamped {@link DataSensitivityClass} name; a null or
     *        unrecognised value is treated as not protected, since an unstamped record is ordinary
     *        clinical data and must not be withheld from the people entitled to see it
     * @param recordCategory         the record's confidential category (e.g. {@code SAFEGUARDING});
     *        a protected record with no category is withheld unless the requester holds a
     *        whole-set grant, because an unlabelled protected record cannot be matched to a grant
     * @param ctx                    trust context for the emergency waiver and its audit line; may
     *        be null, in which case no waiver applies
     */
    public static boolean withholds(String recordSensitivityClass, String recordCategory,
                                    VisibilityProfile profile, TrustContext ctx) {
        if (!isProtected(recordSensitivityClass)) {
            return false;
        }
        if (allowsCategory(recordCategory, profile)) {
            return false;
        }
        if (recordCategory == null || recordCategory.isBlank()) {
            // No category to match a grant against. A whole-set grant still covers it.
            if (profile != null && profile.allowsConfidentialCategory("*")) {
                return false;
            }
        }
        if (emergencyWaiver(ctx, recordCategory)) {
            return false;
        }
        return true;
    }

    /** As above, against the current request and trust context. */
    public static boolean withholds(String recordSensitivityClass, String recordCategory) {
        return withholds(recordSensitivityClass, recordCategory,
                VisibilityContextHolder.current().orElse(null),
                TrustContextHolder.get());
    }

    /**
     * Remove protected rows the requester holds no grant for, leaving ordinary rows and granted
     * categories intact. This is the mixed-collection case the PDP cannot decide: a request for a
     * person's encounters is allowed, but three of the forty encounters are confidential.
     *
     * <p>Never returns null. Rows are dropped per category, so a safeguarding grant keeps the
     * safeguarding rows and still drops the mental-health ones.</p>
     */
    public static <T> List<T> filterProtected(List<T> rows,
                                              Function<T, String> sensitivityOf,
                                              Function<T, String> categoryOf,
                                              VisibilityProfile profile,
                                              TrustContext ctx) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> !withholds(sensitivityOf.apply(row), categoryOf.apply(row), profile, ctx))
                .toList();
    }

    /** As above, against the current request and trust context. */
    public static <T> List<T> filterProtected(List<T> rows,
                                              Function<T, String> sensitivityOf,
                                              Function<T, String> categoryOf) {
        return filterProtected(rows, sensitivityOf, categoryOf,
                VisibilityContextHolder.current().orElse(null),
                TrustContextHolder.get());
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

    /**
     * Emergency / break-glass waiver, mirroring {@code ClinicalAccessGuard.requireCareRelationship}.
     * Logs at WARN naming the actor, so the disclosure is reviewable even if the upstream governance
     * event is lost.
     */
    private static boolean emergencyWaiver(TrustContext ctx, String category) {
        if (ctx == null) {
            return false;
        }
        String purpose = ctx.purposeOfUse() == null ? "" : ctx.purposeOfUse().toUpperCase(Locale.ROOT);
        if (!purpose.equals("EMERGENCY") && !purpose.equals("BREAK_GLASS")) {
            return false;
        }
        log.warn("CONFIDENTIALITY WAIVED (purpose={}): actor={} reading specially-protected content "
                        + "(category={}) without a category grant — emergency/break-glass override. "
                        + "correlation={}",
                purpose, ctx.actorId(), category == null ? "UNSPECIFIED" : category, ctx.correlationId());
        return true;
    }
}
