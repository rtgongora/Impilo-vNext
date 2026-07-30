package zw.gov.mohcc.impilo.experience.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.util.List;

/**
 * Forwards the PDP's visibility obligation from the inbound request onto the BFF's outbound call to a
 * sovereign service — when, and only when, {@code experience.trust.propagate-obligations} is enabled.
 *
 * <h2>The hole this closes</h2>
 * <p>{@code ServiceClientConfig} forwards some forty trust headers and forwards neither
 * {@code x-obligations} nor any flat visibility header. Because the BFF calls services directly
 * ({@code PCT_BASE_URL: http://pct-service:8088}) ext_authz never re-runs on that hop, so every
 * downstream visibility guard sees a null profile on every BFF-mediated call. For
 * {@code SpeciallyProtectedVisibilityGuard}, which fails closed by explicit design ("no profile means
 * the PDP never said yes"), that means every BFF-mediated read of a stamped record would be withheld
 * from everyone <em>including its author</em> once class stamping is enabled — while the direct Envoy
 * route kept working. {@code X-Purpose-Of-Use} IS forwarded, so emergency reads would survive and
 * routine care would not.
 *
 * <h2>Why the flag ships DISABLED, and why that is not caution but a blocker</h2>
 * <p>The pre-check this was gated on has been run against the live preview estate, and it failed
 * worse than expected. The deployed Envoy renders {@code envoy.extAuthz.enabled} false — the running
 * container's config contains <b>no ext_authz filter at all</b>, a single {@code prefix: "/"} route,
 * and <b>no {@code request_headers_to_remove} anywhere</b>. So on that estate:
 *
 * <ul>
 *   <li>there is no PDP at the edge to mint an obligation, and nothing to overwrite a client-supplied
 *       one;</li>
 *   <li>{@code x-obligations} and all ten flat visibility headers are therefore <b>fully forgeable by
 *       any caller</b>;</li>
 *   <li>{@code x-confidential-categories: *} from a browser would satisfy the fail-closed guard
 *       outright.</li>
 * </ul>
 *
 * <p>Which yields an uncomfortable but important conclusion: <b>the BFF's failure to forward these
 * headers is currently the only thing standing between a forged category grant and pct's
 * confidentiality guard.</b> Enabling this flag before the edge strips client-supplied visibility
 * headers would convert a latent hole into a live privilege escalation. Enabling it is therefore
 * blocked on the edge fix, not merely scheduled after it — see
 * {@code docs/clinical-governance/rmnp/srh-confidentiality-stamping.md}.
 *
 * <p>The forwarding is written now, and tested now, so that the flip is a configuration act reviewed
 * on its own merits rather than a code change written under time pressure on the day the withholding
 * starts.
 */
@Component
public class VisibilityObligationPropagator {

    private static final Logger log = LoggerFactory.getLogger(VisibilityObligationPropagator.class);

    /**
     * The obligation blob plus the ten flat headers {@code VisibilityHeaderParser} reads.
     *
     * <p>All eleven travel together on purpose. The parser overlays the nested
     * {@code visibilityProfile} from {@code x-obligations} onto the flat headers field by field, so
     * forwarding a subset would hand a downstream a profile assembled from two different decisions —
     * a partial grant nobody made.
     */
    static final List<String> VISIBILITY_HEADERS = List.of(
            TrustHeaders.OBLIGATIONS,
            TrustHeaders.VISIBILITY_TIER,
            TrustHeaders.PII_ACCESS,
            TrustHeaders.CLINICAL_ACCESS,
            TrustHeaders.AGGREGATE_ONLY,
            TrustHeaders.RESOURCE_SENSITIVITY,
            TrustHeaders.ESCALATION_GRANT_ID,
            TrustHeaders.EXPORT_POLICY,
            TrustHeaders.SUPPRESS_FIELDS,
            TrustHeaders.DRILL_DOWN_ALLOWED,
            TrustHeaders.CONFIDENTIAL_CATEGORIES);

    private final boolean enabled;

    public VisibilityObligationPropagator(
            @Value("${experience.trust.propagate-obligations:false}") boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            log.warn("experience.trust.propagate-obligations is ENABLED: the PDP visibility obligation "
                    + "is now forwarded to downstream services. This is only safe if the edge strips "
                    + "client-supplied visibility headers — otherwise a forged "
                    + "x-confidential-categories reaches the fail-closed confidentiality guard as a "
                    + "grant. Verify the deployed Envoy has ext_authz enabled AND a global "
                    + "request_headers_to_remove covering all eleven visibility headers.");
        }
    }

    /** Whether the obligation is forwarded on this hop. */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Copy the visibility obligation onto the outbound request.
     *
     * <p>Only-if-absent, matching the treatment of actor identity and Authorization in the same
     * interceptor: a client method that deliberately pre-set a narrower obligation must win over blind
     * inbound forwarding. Interceptors run after the client method has built its headers, so a plain
     * set here would silently discard that.
     */
    public void propagate(HttpServletRequest inbound, HttpRequest outbound) {
        if (!enabled) {
            return;
        }
        for (String header : VISIBILITY_HEADERS) {
            if (outbound.getHeaders().containsKey(header)) {
                continue;
            }
            String value = inbound.getHeader(header);
            if (value != null && !value.isBlank()) {
                outbound.getHeaders().set(header, value);
            }
        }
    }
}
