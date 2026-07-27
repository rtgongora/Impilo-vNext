package zw.gov.mohcc.impilo.procedures.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.procedures.api.dto.CompetenceDtos.PrivilegeRecord;
import zw.gov.mohcc.impilo.procedures.core.PrivilegeSource;

import java.util.List;
import java.util.Map;

/**
 * Reads provider privileges from VARAPI, the credentialing system of record. Read-only.
 *
 * <p>Three details here are not guesses — the theatre programme shipped this call wrong three
 * times and found each one live:</p>
 * <ol>
 *   <li>{@code PrivilegeController} wraps the list in an {@code ApiResponse} envelope, so
 *       deserialising the body as a bare {@code List} fails and the check silently degrades to
 *       "unavailable".</li>
 *   <li>The privilege lifecycle has <b>no ACTIVE status</b>. It uses {@code APPROVED}. Matching
 *       on ACTIVE never matches anything, and a check that never matches reads as a check that
 *       never passes.</li>
 *   <li>Scope is stored at the <b>domain</b> level — {@code SURGERY}, not {@code SURGEON}. The
 *       theatre code asked for the role name and got nothing back.</li>
 * </ol>
 *
 * <p>Each of those failed <em>closed</em> and so looked like a working safety check. That is why
 * this client throws on failure rather than returning an empty list: the resolver must be able
 * to tell "no privileges" from "could not ask", and an empty list on error erases the
 * difference. Reporting an outage as a denial is survivable; reporting it as a grant is not, and
 * an empty list means whichever the caller happens to assume.</p>
 */
@Component
public class VarapiPrivilegeClient implements PrivilegeSource {

    private static final Logger log = LoggerFactory.getLogger(VarapiPrivilegeClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VarapiPrivilegeClient(@Value("${procedures.peers.varapi.base-url:http://varapi-service:8083}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PrivilegeRecord> privilegesFor(String providerId) {
        String url = baseUrl + "/v1/internal/providers/" + providerId + "/privileges";
        Map<String, Object> envelope;
        try {
            envelope = restTemplate.getForObject(url, Map.class);
        } catch (RuntimeException e) {
            // Deliberately propagated. See the class docs: an empty list here would be
            // indistinguishable from "this provider holds no privileges".
            log.warn("PROCEDURES: varapi privileges unavailable for {}: {}", providerId, e.toString());
            throw e;
        }
        if (envelope == null) {
            throw new IllegalStateException("varapi returned an empty body for " + providerId);
        }
        Object data = envelope.getOrDefault("data", envelope.get("privileges"));
        if (!(data instanceof List<?> raw)) {
            throw new IllegalStateException(
                    "varapi privilege response had no 'data' list — envelope shape changed?");
        }
        return raw.stream()
                .filter(Map.class::isInstance)
                .map(o -> (Map<String, Object>) o)
                .map(p -> new PrivilegeRecord(
                        str(p, "scope"),
                        firstNonNull(str(p, "privilegeType"), str(p, "type")),
                        firstNonNull(str(p, "status"), str(p, "state")),
                        // The field the previous consumer dropped, and the whole point of §7.
                        firstNonNull(str(p, "supervisingProviderPublicId"), str(p, "supervisingProviderId"))))
                .toList();
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
