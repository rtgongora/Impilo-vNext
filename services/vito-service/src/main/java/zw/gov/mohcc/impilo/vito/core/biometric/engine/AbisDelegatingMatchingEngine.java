package zw.gov.mohcc.impilo.vito.core.biometric.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricTemplateEntity;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link BiometricMatchingEngine} that delegates matching to the dedicated ABIS service
 * (Identity Contract §11, D6 — biometric templates move behind the ABIS boundary; VITO keeps
 * consent/status and an opaque {@code template_ref}).
 *
 * <p>Registered ONLY when {@code impilo.abis.enabled=true} (default off — VITO remains the
 * interim template holder with the fail-closed engine until the ABIS cutover full-boot).
 * {@code @Primary} displaces {@link FailClosedMatchingEngine} without editing any existing
 * file.</p>
 *
 * <p><b>Fail-closed:</b> on ANY transport or protocol error, 1:1 verification returns
 * {@code UNAVAILABLE} at zero confidence and 1:N identification returns no candidates — a
 * broken ABIS link must never fabricate or leak a match. Urgent care is never blocked by a
 * biometric failure (care-first): callers already treat non-MATCH outcomes as deny-and-fallback.</p>
 *
 * <p>The opaque subject reference sent to ABIS is the VITO biometric profile id when present
 * (a UUID unrelated to the Health ID), else a synthetic {@code vito-template:<id>} pointer —
 * never a Health ID. TSHEPO holds the biometric-subject → HID mapping.</p>
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "impilo.abis", name = "enabled", havingValue = "true")
public class AbisDelegatingMatchingEngine implements BiometricMatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(AbisDelegatingMatchingEngine.class);

    private static final Set<String> ABIS_IDENTIFY_REASONS = Set.of("ENROLMENT", "RECOVERY", "DEDUPLICATION");
    private static final String DEFAULT_IDENTIFY_REASON = "DEDUPLICATION";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public AbisDelegatingMatchingEngine(
            @Value("${ABIS_BASE_URL:http://abis-service:8186}") String baseUrl) {
        this(defaultRestTemplate(), baseUrl);
    }

    /** Visible for tests: inject a mocked {@link RestTemplate}. */
    AbisDelegatingMatchingEngine(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    @Override
    public VerificationDecision verify(
            byte[] probeTemplate, BiometricTemplateEntity enrolled, BiometricProbeContext probeContext) {
        if (probeTemplate == null || enrolled == null) {
            return new VerificationDecision("NO_REFERENCE", 0.0, "Missing probe or enrolled template");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("subjectRef", subjectRefOf(enrolled));
            body.put("modality", enrolled.getModality().name());
            if (enrolled.getPosition() != null) {
                body.put("position", enrolled.getPosition());
            }
            body.put("probeBase64", Base64.getEncoder().encodeToString(probeTemplate));
            if (probeContext != null) {
                body.put("livenessScore", probeContext.livenessScore());
                body.put("deviceAttestationRef", probeContext.deviceAttestationRef());
            }

            ResponseEntity<AbisVerifyResponse> response = restTemplate.exchange(
                    baseUrl + "/v1/abis/verify", HttpMethod.POST,
                    new HttpEntity<>(body, headers(enrolled.getTenantId(), null)),
                    AbisVerifyResponse.class);
            AbisVerifyResponse decision = response.getBody();
            if (decision == null || decision.decision() == null) {
                return unavailable("ABIS returned an empty verification decision");
            }
            return new VerificationDecision(
                    decision.decision(),
                    decision.confidence() == null ? 0.0 : decision.confidence(),
                    decision.summary() == null ? "ABIS verification decision" : decision.summary());
        } catch (RuntimeException e) {
            // Fail closed on ANY transport/protocol error — never fabricate a match.
            log.warn("ABIS verify call failed — failing closed (UNAVAILABLE): {}", e.getMessage());
            return unavailable("ABIS unreachable or errored — verification fails closed: " + e.getMessage());
        }
    }

    @Override
    public List<IdentificationCandidate> identify(
            byte[] probeTemplate, List<BiometricTemplateEntity> candidates, BiometricProbeContext probeContext) {
        if (probeTemplate == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, BiometricTemplateEntity> bySubjectRef = new HashMap<>();
            for (BiometricTemplateEntity candidate : candidates) {
                bySubjectRef.putIfAbsent(subjectRefOf(candidate), candidate);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("modality", candidates.get(0).getModality().name());
            body.put("probeBase64", Base64.getEncoder().encodeToString(probeTemplate));
            if (probeContext != null) {
                body.put("livenessScore", probeContext.livenessScore());
                body.put("deviceAttestationRef", probeContext.deviceAttestationRef());
            }

            ResponseEntity<AbisIdentifyResponse> response = restTemplate.exchange(
                    baseUrl + "/v1/abis/identify", HttpMethod.POST,
                    new HttpEntity<>(body, headers(candidates.get(0).getTenantId(), identifyReason(probeContext))),
                    AbisIdentifyResponse.class);
            AbisIdentifyResponse identify = response.getBody();
            if (identify == null || identify.candidates() == null) {
                return List.of();
            }

            // Candidates for adjudication only — map opaque subject refs back to the candidate
            // entities VITO supplied; anything ABIS returns outside that set is dropped (no
            // cross-population disclosure and NEVER an automatic merge).
            List<IdentificationCandidate> result = new ArrayList<>();
            for (AbisIdentifyCandidate candidate : identify.candidates()) {
                BiometricTemplateEntity match = bySubjectRef.get(candidate.subjectRef());
                if (match != null) {
                    result.add(new IdentificationCandidate(
                            match.getHealthId(),
                            candidate.confidence() == null ? 0.0 : candidate.confidence(),
                            candidate.summary() == null ? "ABIS candidate for adjudication" : candidate.summary()));
                }
            }
            return List.copyOf(result);
        } catch (RuntimeException e) {
            // Fail closed on ANY transport/protocol error — no fabricated candidates.
            log.warn("ABIS identify call failed — failing closed (no candidates): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Opaque ABIS subject reference for a VITO template row: the biometric profile id when
     * present (UUID unrelated to the Health ID), else a synthetic per-row pointer. Never a
     * Health ID.
     */
    private static String subjectRefOf(BiometricTemplateEntity entity) {
        UUID profileId = entity.getProfileId();
        return profileId != null ? profileId.toString() : "vito-template:" + entity.getId();
    }

    /**
     * ABIS restricts 1:N to ENROLMENT / RECOVERY / DEDUPLICATION. VITO's 1:N calls are
     * dedup/enrolment flows; the probe-context engine hint may carry an explicit reason.
     */
    private static String identifyReason(BiometricProbeContext probeContext) {
        if (probeContext != null && probeContext.engineHint() != null) {
            String hint = probeContext.engineHint().trim().toUpperCase(Locale.ROOT);
            if (ABIS_IDENTIFY_REASONS.contains(hint)) {
                return hint;
            }
        }
        return DEFAULT_IDENTIFY_REASON;
    }

    private static HttpHeaders headers(UUID tenantId, String identifyReason) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantId != null) {
            headers.set("X-Tenant-ID", tenantId.toString());
        }
        if (identifyReason != null) {
            headers.set("X-Identify-Reason", identifyReason);
        }
        return headers;
    }

    private static VerificationDecision unavailable(String summary) {
        return new VerificationDecision("UNAVAILABLE", 0.0, summary);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AbisVerifyResponse(String decision, Double confidence, String summary) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AbisIdentifyResponse(String reason, String decision, List<AbisIdentifyCandidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AbisIdentifyCandidate(String subjectRef, Double confidence, String summary) {}
}
