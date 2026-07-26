package zw.gov.mohcc.impilo.pct.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The patient's current medicines, read from OROS.
 *
 * <p>Exists because interaction checking, duplicate-therapy detection and deprescribing all need a
 * medicine list, and {@code PatientFacts} carried none — those requirements were unimplementable
 * rather than unimplemented. This is the outbound half.</p>
 *
 * <p>OROS is an OAuth2 resource server whose {@code /v1/**} is {@code authenticated()} (checked on
 * its {@code SecurityConfig} before this client was written — a role-guarded target would 403
 * rather than authenticate, and finding that out afterwards wastes the round trip). Trust headers
 * are context, not credentials, so the bearer token from {@link ServiceTokenProvider} is what makes
 * the call succeed.</p>
 *
 * <p><strong>Unreachable is not "takes no medicines".</strong> Every failure path returns
 * {@code null} rather than an empty list. An empty list is an affirmative clinical claim — it is
 * what an interaction checker reads as "nothing to interact with" — and returning one because OROS
 * was down would produce a confident all-clear from a system that could not ask. Null leaves the
 * fact absent so a rule needing it declines to score and says why.</p>
 */
@Component
public class OrosMedicationIntegration {

    private static final Logger log = LoggerFactory.getLogger(OrosMedicationIntegration.class);

    private final RestTemplate restTemplate;
    private final ServiceTokenProvider tokenProvider;
    private final String baseUrl;

    public OrosMedicationIntegration(
            RestTemplate restTemplate,
            ServiceTokenProvider tokenProvider,
            @Value("${pct.integration.oros.base-url:${OROS_BASE_URL:http://localhost:8089}}")
            String baseUrl) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
        this.baseUrl = baseUrl;
    }

    /**
     * Codes of the medicines this patient is currently on.
     *
     * @return the codes, or {@code null} when OROS could not be asked. Never an empty list on
     *         failure — see the class note.
     */
    @SuppressWarnings("unchecked")
    public List<String> currentMedicationCodes(UUID tenantId, String subjectCpid) {
        if (subjectCpid == null || subjectCpid.isBlank() || !tokenProvider.isConfigured()) {
            // Unconfigured is indistinguishable from unknown, and must not read as "no medicines".
            return null;
        }
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/v1/prescriptions/patient/" + subjectCpid,
                    HttpMethod.GET, new HttpEntity<>(headers(tenantId)), Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("OROS returned {} for the medicine list — recorded as unavailable, not as none",
                        response.getStatusCode());
                return null;
            }
            Object data = response.getBody().get("data");
            if (!(data instanceof List<?> rows)) {
                return null;
            }
            return codesOf(rows);
        } catch (RestClientException e) {
            log.warn("OROS unreachable for the medicine list: {} — the fact is recorded as absent so "
                     + "a rule needing it declines to score, rather than as an empty medicine list",
                    e.getMessage());
            return null;
        }
    }

    /** Distinct codes, preserving order, from whichever coded field a prescription row carries. */
    private static List<String> codesOf(List<?> rows) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> m)) continue;
            if (!isCurrent(str(m.get("status")))) continue;
            String code = firstNonBlank(
                    str(m.get("medication_code")), str(m.get("medicationCode")),
                    str(m.get("atc_code")), str(m.get("atcCode")), str(m.get("code")));
            if (code != null) {
                codes.add(code);
            }
        }
        return new ArrayList<>(codes);
    }

    /**
     * Whether a prescription still represents something the patient is taking.
     *
     * <p>An unknown status counts as current. A medicine whose status nobody recorded is more
     * safely treated as still being taken than as stopped: the cost of a spurious interaction
     * warning is an explanation, and the cost of a missed one is the interaction.</p>
     */
    private static boolean isCurrent(String status) {
        if (status == null) return true;
        return switch (status.toUpperCase(java.util.Locale.ROOT)) {
            case "CANCELLED", "STOPPED", "COMPLETED", "EXPIRED", "SUPERSEDED" -> false;
            default -> true;
        };
    }

    private HttpHeaders headers(UUID tenantId) {
        HttpHeaders h = new HttpHeaders();
        if (tenantId != null) {
            h.set("X-Tenant-ID", tenantId.toString());
        }
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        h.set("X-Correlation-ID", UUID.randomUUID().toString());
        h.set("X-Actor-ID", "pct-service");
        h.set("X-Actor-Type", "SERVICE");
        h.set("X-Purpose-Of-Use", "TREATMENT");
        // Trust headers are context, not authentication. Without the bearer token this is a 401
        // that surfaces as "medicines unavailable" — honest, and useless to the prescriber.
        Optional<String> token = tokenProvider.accessToken();
        token.ifPresent(h::setBearerAuth);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
