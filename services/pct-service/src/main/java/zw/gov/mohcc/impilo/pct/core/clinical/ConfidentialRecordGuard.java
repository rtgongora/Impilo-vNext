package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.visibility.SpeciallyProtectedVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Consumes the PDP's confidential-category grants for pct's reproductive records — the read half of
 * the seam whose write half is {@link ConfidentialityStamper}.
 *
 * <h2>This guard NEVER throws, and that is enforced by its return types</h2>
 * <p>A withheld row becomes an absent row; a withheld record becomes {@link Optional#empty()}. There
 * is no code path from here to a 403, so nobody can add one by accident. The reason is in
 * {@code SpeciallyProtectedVisibilityGuard}'s own javadoc: a 403 distinguishable from a 404 tells the
 * guardian that the confidential record IS there, which is most of what confidentiality was
 * protecting. A caller must answer a withheld record exactly as it answers one that does not exist.
 *
 * <p>This is deliberately the OPPOSITE of {@link ClinicalAccessGuard}, which throws 403. That
 * asymmetry is correct and must not be "tidied up": {@code ClinicalAccessGuard} rejects <em>writes</em>,
 * and refusing a write reveals nothing about whether a record exists.
 *
 * <h2>Shadow observability is what makes the governance flip safe</h2>
 * <p>Before the flip, every record's {@code sensitivity_class} is {@code FULL_CLINICAL}, so nothing is
 * withheld and this wiring is inert — matching the PDP's own SHADOW mode. But a record already
 * carries a truthful category, so this guard can report what WOULD be withheld once the flip happens.
 * That signal turns the flip from a leap into a measurement: you can see, in advance, how many reads
 * would start returning fewer rows and to whom.
 */
@Component
public class ConfidentialRecordGuard {

    private static final Logger log = LoggerFactory.getLogger(ConfidentialRecordGuard.class);

    /**
     * Drop rows this requester holds no grant for. Ordinary rows and granted categories survive; rows
     * are dropped per category, so a safeguarding grant keeps safeguarding rows and still drops SRH.
     */
    public <T> List<T> filter(List<T> rows,
                              Function<T, String> classOf,
                              Function<T, String> categoryOf) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        VisibilityProfile profile = VisibilityContextHolder.current().orElse(null);
        TrustContext ctx = TrustContextHolder.get();

        reportShadowWithholds(rows, classOf, categoryOf, profile, ctx);

        return SpeciallyProtectedVisibilityGuard.filterProtected(rows, classOf, categoryOf, profile, ctx);
    }

    /** As {@link #filter}, for a single record. A withheld record is indistinguishable from absent. */
    public <T> Optional<T> visible(Optional<T> row,
                                   Function<T, String> classOf,
                                   Function<T, String> categoryOf) {
        if (row == null || row.isEmpty()) {
            return Optional.empty();
        }
        List<T> kept = filter(List.of(row.get()), classOf, categoryOf);
        return kept.isEmpty() ? Optional.empty() : Optional.of(kept.get(0));
    }

    /**
     * Report rows that carry a category this requester holds no grant for — whether or not they are
     * currently stamped protected. Before the flip this is the only visible effect of the seam, and
     * it is the measurement the flip decision should rest on.
     */
    private <T> void reportShadowWithholds(List<T> rows,
                                           Function<T, String> classOf,
                                           Function<T, String> categoryOf,
                                           VisibilityProfile profile,
                                           TrustContext ctx) {
        int wouldWithhold = 0;
        for (T row : rows) {
            String category = categoryOf.apply(row);
            if (category == null || category.isBlank()) {
                continue;
            }
            boolean granted = profile != null
                    && (profile.allowsConfidentialCategory(category) || profile.allowsConfidentialCategory("*"));
            if (granted) {
                continue;
            }
            // Already withheld for real? Then it is not a shadow observation, it is the live seam.
            if (SpeciallyProtectedVisibilityGuard.isProtected(classOf.apply(row))) {
                continue;
            }
            wouldWithhold++;
        }
        if (wouldWithhold > 0) {
            log.info("pct.confidentiality.shadow_withhold count={} actor={} purpose={} correlation={} "
                            + "— these rows carry a confidential category the requester holds no grant for "
                            + "and WOULD be withheld once class stamping is enabled",
                    wouldWithhold,
                    ctx == null ? "unknown" : ctx.actorId(),
                    ctx == null ? "unknown" : ctx.purposeOfUse(),
                    ctx == null ? "unknown" : ctx.correlationId());
        }
    }
}
