package zw.gov.mohcc.impilo.ubomi.integration.rg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The <b>only</b> class that talks to the Registrar-General (RG / CRVS) API.
 *
 * <p>Bean exists <b>only</b> when {@code ubomi.rg.enabled=true}
 * ({@link ConditionalOnProperty}) — with the flag off, ubomi gains no RG HTTP
 * client bean whatsoever. On construction it enforces two startup invariants
 * (fail at boot, never a runtime surprise):</p>
 * <ol>
 *   <li>the configured {@code base-url} host must be on the {@code allowed-hosts}
 *       allow-list; and</li>
 *   <li>the mTLS custody refs ({@code ubomi.rg.mtls.key-ref} /
 *       {@code truststore-ref}) must be present — the RG link is mTLS-only and
 *       never carries inline PEM.</li>
 * </ol>
 *
 * <p>Failure posture: an authoritative <em>non-match</em> is a normal
 * {@link RgVerificationOutcome} ({@code matched=false}); a transport failure,
 * timeout, or 5xx is an {@link RgUnavailableException} so the caller can
 * fail-open-for-care and park {@code RG_PENDING}.</p>
 *
 * <p>Response minimisation: the wire response is deserialized into
 * {@link RgWireResponse} which ignores unknown properties, and only the minimal
 * fields are copied into {@link RgVerificationOutcome}. The full RG record never
 * escapes this class.</p>
 */
@Component
@ConditionalOnProperty(prefix = "ubomi.rg", name = "enabled", havingValue = "true")
public class RegistrarGeneralClient {

    private static final Logger log = LoggerFactory.getLogger(RegistrarGeneralClient.class);

    private final RestTemplate restTemplate;
    private final UbomiRgProperties props;

    public RegistrarGeneralClient(@Qualifier("rgRestTemplate") RestTemplate restTemplate,
                                  UbomiRgProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
        validateAllowList();
        validateMtlsRefs();
        log.info("RegistrarGeneralClient enabled; base-url host allow-listed, mTLS refs present");
    }

    private void validateAllowList() {
        String baseUrl = props.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "ubomi.rg.enabled=true but ubomi.rg.base-url is blank");
        }
        String host;
        try {
            host = URI.create(baseUrl).getHost();
        } catch (Exception e) {
            throw new IllegalStateException("ubomi.rg.base-url is not a valid URI: " + baseUrl, e);
        }
        if (host == null || props.getAllowedHosts() == null
                || props.getAllowedHosts().stream().noneMatch(host::equalsIgnoreCase)) {
            throw new IllegalStateException(
                    "ubomi.rg.base-url host '" + host + "' is not on ubomi.rg.allowed-hosts "
                            + props.getAllowedHosts());
        }
    }

    private void validateMtlsRefs() {
        UbomiRgProperties.Mtls mtls = props.getMtls();
        if (mtls.getKeyRef() == null || mtls.getKeyRef().isBlank()
                || mtls.getTruststoreRef() == null || mtls.getTruststoreRef().isBlank()) {
            throw new IllegalStateException(
                    "ubomi.rg.enabled=true requires ubomi.rg.mtls.key-ref and "
                            + "ubomi.rg.mtls.truststore-ref (tshepo-keys custody refs)");
        }
    }

    /**
     * Verify an identifier (National ID / passport / birth-registration number)
     * against the RG. The raw identifier is transmitted to the RG but never
     * returned or stored — the caller persists only a one-way hash.
     *
     * @throws RgUnavailableException on timeout / connection failure / 5xx
     */
    public RgVerificationOutcome verify(String verificationType, String identifier,
                                        String fullName, String dateOfBirth) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("verificationType", verificationType);
        body.put("identifier", identifier);
        if (fullName != null) body.put("fullName", fullName);
        if (dateOfBirth != null) body.put("dateOfBirth", dateOfBirth);

        String url = props.getBaseUrl() + "/v1/verify";
        try {
            ResponseEntity<RgWireResponse> resp =
                    restTemplate.postForEntity(url, body, RgWireResponse.class);
            RgWireResponse wire = resp.getBody();
            if (wire == null || !wire.matched) {
                return RgVerificationOutcome.noMatch();
            }
            return new RgVerificationOutcome(
                    true,
                    wire.civilIdentityToken,
                    wire.verifiedFullName,
                    wire.verifiedDateOfBirth);
        } catch (HttpClientErrorException.NotFound nf) {
            // Authoritative non-match — normal outcome, not an outage.
            return RgVerificationOutcome.noMatch();
        } catch (HttpClientErrorException ce) {
            HttpStatusCode sc = ce.getStatusCode();
            // Other 4xx are non-authoritative client errors — treat as unavailable
            // (fail-open) rather than fabricating a verdict.
            throw new RgUnavailableException("RG rejected verify request: " + sc, ce);
        } catch (RestClientException e) {
            // Timeout, connection refused, 5xx, malformed response.
            throw new RgUnavailableException("RG unavailable for verify: " + e.getMessage(), e);
        }
    }

    /**
     * Confirm a birth/death registration package with the RG.
     *
     * @throws RgUnavailableException on timeout / connection failure / 5xx
     */
    public RgVerificationOutcome confirmRegistration(String verificationType, String referenceHash) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("verificationType", verificationType);
        body.put("referenceHash", referenceHash);

        String url = props.getBaseUrl() + "/v1/registrations/confirm";
        try {
            ResponseEntity<RgWireResponse> resp =
                    restTemplate.postForEntity(url, body, RgWireResponse.class);
            RgWireResponse wire = resp.getBody();
            if (wire == null || !wire.matched) {
                return RgVerificationOutcome.noMatch();
            }
            return new RgVerificationOutcome(true, wire.civilIdentityToken, false, false);
        } catch (HttpClientErrorException.NotFound nf) {
            return RgVerificationOutcome.noMatch();
        } catch (RestClientException e) {
            throw new RgUnavailableException("RG unavailable for confirm: " + e.getMessage(), e);
        }
    }

    /**
     * Wire-format response. {@code @JsonIgnoreProperties(ignoreUnknown=true)} is
     * the enforcement point: any additional civil PII the RG returns (National
     * ID, names, address, parents, ...) is silently dropped at deserialization
     * and never enters the ubomi process.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class RgWireResponse {
        public boolean matched;
        public String civilIdentityToken;
        public boolean verifiedFullName;
        public boolean verifiedDateOfBirth;
    }
}
