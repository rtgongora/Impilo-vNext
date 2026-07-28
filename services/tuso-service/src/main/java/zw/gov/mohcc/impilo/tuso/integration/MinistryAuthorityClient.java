package zw.gov.mohcc.impilo.tuso.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Reads a person's regulatory/Ministry appointments from the organization registry (FCV-W2).
 *
 * <p>Verifying a facility is an act of Ministry authority, so who holds that authority — and over
 * which jurisdiction — must come from the system of record for appointments, never from a request
 * header. The verification lane previously decided this from {@code X-Actor-Type}, a value the
 * caller supplies.</p>
 *
 * <p><b>Degrades closed.</b> If org-registry is unreachable this returns an empty list, which
 * denies the decision rather than granting it. That is the correct failure direction for an
 * authority lookup, and it is why the outage case is a refusal the operator can see rather than a
 * silent allow.</p>
 */
@Service
public class MinistryAuthorityClient {

    private static final Logger log = LoggerFactory.getLogger(MinistryAuthorityClient.class);

    private final RestClient restClient;

    public MinistryAuthorityClient(TusoProperties tusoProperties, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(tusoProperties.getOrgRegistry().getBaseUrl())
                .build();
    }

    /**
     * Every appointment recorded for this person, across organisations. Filtering to ACTIVE, to the
     * Ministry, and to the roles that may verify is the caller's job — this client only fetches.
     *
     * @return appointment maps; empty when the person has none or the registry is unavailable
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> appointmentsForPerson(String personHealthId) {
        if (personHealthId == null || personHealthId.isBlank()) {
            return List.of();
        }
        try {
            Object body = restClient.get()
                    .uri("/v1/regulatory/appointments/by-person/{id}",
                            URLEncoder.encode(personHealthId, StandardCharsets.UTF_8))
                    .retrieve()
                    .body(Object.class);
            if (body instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
            // Tolerate an enveloped response without assuming one.
            if (body instanceof Map<?, ?> map && map.get("data") instanceof List<?> data) {
                return (List<Map<String, Object>>) data;
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Organization registry unavailable while resolving appointments for a verifier: {}",
                    e.getMessage());
            return List.of();
        }
    }
}
