package zw.gov.mohcc.impilo.tshepo.sdk.envelope;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.io.IOException;

/**
 * Rejects requests that did not come through the PDP (D3).
 *
 * <p>Every service already reads {@code x-decision: ALLOW} and the obligation headers, and
 * every one of those is a header a caller can simply type. On a cluster where pod isolation
 * is not enforced — which this one is not, see
 * {@code docs/environment/NETWORK_POLICY_ENFORCEMENT_DEPENDENCY.md} — anything inside the
 * cluster can call a service directly and present exactly the headers it wants. This filter
 * is what makes the difference between a claim and evidence.</p>
 *
 * <h2>Rolling it out</h2>
 *
 * <p>{@code OFF} → {@code SHADOW} → {@code ENFORCE}, and SHADOW is not a formality. It logs
 * what ENFORCE would have rejected, which is the only way to find the callers that reach
 * this service without traversing the gateway before switching them off. Going straight to
 * ENFORCE turns every one of them into a 403 at once.</p>
 *
 * <p>ENFORCE also makes keys-service a hard dependency of this service: the PDP allows
 * without an envelope when it cannot sign, so a keys-service outage arrives here as traffic
 * with no envelope. Check {@code DECISION_ENVELOPE_UNSIGNED} is quiet in the PDP's logs
 * before enabling it.</p>
 */
public class DecisionEnvelopeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DecisionEnvelopeFilter.class);

    /** Grep target for what ENFORCE would reject, while still in SHADOW. */
    public static final String SHADOW_MARKER = "DECISION_ENVELOPE_SHADOW_REJECT";
    /** Grep target for an actual rejection. */
    public static final String ENFORCE_MARKER = "DECISION_ENVELOPE_REJECTED";

    private final DecisionEnvelopeVerifier verifier;
    private final DecisionEnvelopeProperties properties;

    public DecisionEnvelopeFilter(DecisionEnvelopeVerifier verifier,
                                  DecisionEnvelopeProperties properties) {
        this.verifier = verifier;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (properties.getMode() == DecisionEnvelopeProperties.Mode.OFF) {
            return true;
        }
        String path = request.getRequestURI();
        return properties.getExemptPathPrefixes().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        DecisionEnvelopeVerifier.RequestFacts facts = new DecisionEnvelopeVerifier.RequestFacts(
                request.getHeader(TrustHeaders.DECISION_ENVELOPE),
                request.getHeader(TrustHeaders.ACTOR_ID),
                request.getHeader(TrustHeaders.TENANT_ID),
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader(TrustHeaders.OBLIGATIONS));

        DecisionEnvelopeVerifier.Result result = verifier.verify(facts);

        if (result.valid()) {
            chain.doFilter(request, response);
            return;
        }

        if (properties.getMode() == DecisionEnvelopeProperties.Mode.SHADOW) {
            log.warn("{}: {} {} would be rejected — {} ({})", SHADOW_MARKER,
                    request.getMethod(), request.getRequestURI(),
                    result.rejection(), result.detail());
            chain.doFilter(request, response);
            return;
        }

        log.warn("{}: {} {} — {} ({})", ENFORCE_MARKER,
                request.getMethod(), request.getRequestURI(),
                result.rejection(), result.detail());
        // The reason goes to the log, not the body: telling a caller which binding check
        // failed tells them what to change to get past it.
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Request was not authorised by Tshepo");
    }
}
