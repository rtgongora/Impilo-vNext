package zw.gov.mohcc.impilo.companion.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Patient-context guard for clinical services (Identity Contract §7.2 / B5).
 *
 * <p>The structural rule: a clinical service only accepts a subject key
 * ({@code X-Subject-ID}) that was minted by the Identity Trust Core. Since a CPID
 * and a Health ID are both UUIDs, value-shape checks cannot distinguish them —
 * possession of a <b>patient-context scoped token</b> whose {@code sub_ref} claim
 * matches the header is the proof. Tokens are issued by tshepo-identity
 * ({@code POST /v1/identity/tokens}, EdDSA via tshepo-keys).</p>
 *
 * <p>Modes ({@code impilo.subject-context.mode}, default {@code off}):</p>
 * <ul>
 *   <li>{@code off} — filter inactive (pre-cutover default).</li>
 *   <li>{@code log} — mismatches and missing tokens are logged with a
 *       {@code SUBJECT_CONTEXT_VIOLATION} marker; requests proceed. Use this to
 *       burn in the token plumbing estate-wide.</li>
 *   <li>{@code enforce} — writes (POST/PUT/PATCH/DELETE) carrying
 *       {@code X-Subject-ID} without a matching token are rejected 403.</li>
 * </ul>
 *
 * <p>This filter checks the token's {@code sub_ref} == header binding locally by
 * decoding the JWS payload; full signature verification/revocation is delegated
 * to the issuing service's introspection when {@code enforce} mode is combined
 * with the service's own authz stack. Reads are never blocked (care-first).</p>
 */
public class SubjectContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SubjectContextFilter.class);

    /** Bearer-style header carrying the patient-context scoped token. */
    public static final String SUBJECT_TOKEN_HEADER = "X-Subject-Context-Token";

    public enum Mode { OFF, LOG, ENFORCE }

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final Mode mode;

    public SubjectContextFilter(String mode) {
        Mode parsed;
        try {
            parsed = Mode.valueOf(mode == null ? "OFF" : mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parsed = Mode.OFF;
        }
        this.mode = parsed;
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (mode == Mode.OFF || !(request instanceof HttpServletRequest httpReq)) {
            chain.doFilter(request, response);
            return;
        }
        String subjectId = httpReq.getHeader(CompanionHeaders.SUBJECT_ID);
        boolean isWrite = WRITE_METHODS.contains(httpReq.getMethod());
        if (subjectId == null || subjectId.isBlank() || !isWrite) {
            chain.doFilter(request, response);
            return;
        }

        String token = httpReq.getHeader(SUBJECT_TOKEN_HEADER);
        String violation = validate(subjectId, token);
        if (violation == null) {
            chain.doFilter(request, response);
            return;
        }

        if (mode == Mode.LOG) {
            log.warn("SUBJECT_CONTEXT_VIOLATION ({}): {} {} subject={}",
                    violation, httpReq.getMethod(), httpReq.getRequestURI(), subjectId);
            chain.doFilter(request, response);
            return;
        }

        HttpServletResponse httpResp = (HttpServletResponse) response;
        httpResp.setStatus(403);
        httpResp.setContentType("application/json");
        httpResp.getWriter().write("{\"error\":\"SUBJECT_CONTEXT_REQUIRED\",\"message\":\""
                + "Write with X-Subject-ID requires a matching patient-context token ("
                + violation + ")\"}");
    }

    /** @return null when valid, else a violation code. */
    static String validate(String subjectId, String token) {
        if (token == null || token.isBlank()) {
            return "MISSING_TOKEN";
        }
        String subRef = extractSubRef(token);
        if (subRef == null) {
            return "UNPARSEABLE_TOKEN";
        }
        return subRef.equals(subjectId) ? null : "SUBJECT_MISMATCH";
    }

    /** Decodes the JWS payload (no signature check here) and reads {@code sub_ref}. */
    private static String extractSubRef(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            // minimal extraction without a JSON dependency: "sub_ref":"<value>"
            int idx = payload.indexOf("\"sub_ref\"");
            if (idx < 0) {
                return null;
            }
            int colon = payload.indexOf(':', idx);
            int firstQuote = payload.indexOf('"', colon + 1);
            int secondQuote = payload.indexOf('"', firstQuote + 1);
            if (firstQuote < 0 || secondQuote < 0) {
                return null;
            }
            return payload.substring(firstQuote + 1, secondQuote);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
