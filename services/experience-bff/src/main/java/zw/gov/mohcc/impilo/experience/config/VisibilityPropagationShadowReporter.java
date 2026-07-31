package zw.gov.mohcc.impilo.experience.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityHeaderParser;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures, without changing anything, how far the estate would tighten if the BFF began forwarding
 * the visibility obligation to downstream services.
 *
 * <h2>Why a measurement has to come first</h2>
 * <p>The BFF calls sovereign services directly ({@code PCT_BASE_URL: http://pct-service:8088}), so
 * ext_authz never re-runs on that hop and a downstream sees only what
 * {@link ServiceClientConfig#trustHeaderForwardingInterceptor} forwards — which today includes
 * neither {@code x-obligations} nor any flat visibility header. Every visibility guard downstream
 * therefore sees a null profile on every BFF-mediated call, and the three guards disagree about what
 * a null profile means:
 *
 * <ul>
 *   <li>{@code AggregateVisibilityGuard} and {@code ClinicalVisibilityGuard} are <b>permissive</b> on
 *       a null profile and restrictive on a present one — so forwarding would <em>tighten</em> them
 *       estate-wide, on routes nobody has enumerated.</li>
 *   <li>{@code SpeciallyProtectedVisibilityGuard} is <b>fail-closed</b> on a null profile — so NOT
 *       forwarding withholds every stamped record from everyone, including its author, the moment
 *       class stamping is enabled.</li>
 * </ul>
 *
 * <p>Those two pull in opposite directions, which is exactly why the propagation is gated and why
 * this reporter exists: it turns "how much would this tighten?" from a guess into a number that can
 * be read off the logs of a running estate before anything changes. It is the same pattern as
 * {@code pct.confidentiality.shadow_withhold}.
 *
 * <h2>This class must never change behaviour</h2>
 * <p>It reads headers and logs. It has no way to alter the outbound request — the outbound
 * {@link HttpRequest} is accepted for its URI only, and nothing here touches its headers. Any future
 * edit that makes this class mutate a request has misunderstood its purpose.
 */
@Component
public class VisibilityPropagationShadowReporter {

    private static final Logger log = LoggerFactory.getLogger(VisibilityPropagationShadowReporter.class);

    /** Clinical-access levels that restrict. Anything else is either full access or unstated. */
    private static final List<String> RESTRICTIVE_CLINICAL_ACCESS = List.of("NONE", "SUMMARY_ONLY");

    private final ObjectMapper objectMapper;

    public VisibilityPropagationShadowReporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Log which visibility axes WOULD restrict this outbound call if the obligation were forwarded.
     *
     * <p>Silent when the inbound request carries no obligation, because there is then nothing to
     * forward and nothing would change. Silent, too, when a profile is present but restricts nothing:
     * a log line per unaffected call would bury the signal it exists to produce.
     */
    void report(HttpServletRequest inbound, HttpRequest outbound) {
        VisibilityProfile profile;
        try {
            profile = VisibilityHeaderParser.resolve(inbound, objectMapper);
        } catch (RuntimeException e) {
            // A measurement must never be able to fail a request it is only observing.
            log.debug("experience.visibility.shadow_restrict could not resolve a profile: {}", e.getMessage());
            return;
        }
        if (profile == null) {
            return;
        }
        List<String> axes = restrictingAxes(profile);
        if (axes.isEmpty()) {
            return;
        }
        log.info("experience.visibility.shadow_restrict route={} downstream={} axes={} "
                        + "— forwarding the obligation to this downstream WOULD apply these "
                        + "restrictions, which the downstream does not currently see",
                inbound.getRequestURI(),
                outbound.getURI() == null ? "unknown" : outbound.getURI().getHost(),
                String.join(",", axes));
    }

    /**
     * The axes on which a present profile is more restrictive than the null profile a downstream sees
     * today.
     *
     * <p>{@code confidentialCategories} is reported when ABSENT, not when present: absent is what
     * withholds specially-protected content, and a granted category is the permissive case.
     */
    private static List<String> restrictingAxes(VisibilityProfile profile) {
        List<String> axes = new ArrayList<>();
        if (Boolean.TRUE.equals(profile.aggregateOnly())) {
            axes.add("aggregateOnly");
        }
        if (profile.clinicalAccess() != null
                && RESTRICTIVE_CLINICAL_ACCESS.contains(profile.clinicalAccess().trim().toUpperCase(java.util.Locale.ROOT))) {
            axes.add("clinicalAccess=" + profile.clinicalAccess());
        }
        if (profile.confidentialCategories() == null || profile.confidentialCategories().isEmpty()) {
            axes.add("confidentialCategoriesAbsent");
        }
        if (Boolean.FALSE.equals(profile.drillDownAllowed())) {
            axes.add("drillDownDenied");
        }
        if (profile.suppressFields() != null && !profile.suppressFields().isEmpty()) {
            axes.add("suppressFields=" + profile.suppressFields().size());
        }
        return axes;
    }
}
