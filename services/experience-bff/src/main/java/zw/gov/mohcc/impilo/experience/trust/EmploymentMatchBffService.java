package zw.gov.mohcc.impilo.experience.trust;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.ProviderTrustClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Channel B (public workforce) trust composition (IATG Wave-1, WS-D).
 *
 * <p>Given a Health ID and an EC number, asks workforce-governance (the HSC
 * employment system of record) for a match, and — when employment is confirmed
 * and a provider registration exists — asserts EMPLOYMENT_MATCHED on the VARAPI
 * provider trust ledger with masked EC evidence.</p>
 *
 * <p>Doctrine guardrails:</p>
 * <ul>
 *   <li>the raw EC number never appears in responses or evidence refs — only
 *       the {@code first-2-chars + "***"} mask;</li>
 *   <li>NOT_FOUND / CONFLICT never produce a trust assertion;</li>
 *   <li>Wave 1 does NOT create providers: a match without a provider comes back
 *       with {@code providerCreationRequired=true} guidance instead;</li>
 *   <li>a trust rejection (e.g. 409 downgrade guard) or unavailable registry is
 *       reported honestly, never retried into a different level.</li>
 * </ul>
 */
@Service
public class EmploymentMatchBffService {

    private static final Logger log = LoggerFactory.getLogger(EmploymentMatchBffService.class);

    static final String TARGET_LEVEL_EMPLOYMENT_MATCHED = "EMPLOYMENT_MATCHED";
    static final String EVIDENCE_TYPE_EC_MATCH = "EC_MATCH";
    static final String EVIDENCE_SOURCE_HSC_EMPLOYMENT = "HSC_EMPLOYMENT";
    static final String CHANNEL_WORKFORCE_B = "WORKFORCE_B";
    static final double CONFIDENCE_MATCHED_BOTH = 0.95;
    static final double CONFIDENCE_EMPLOYMENT_ONLY = 0.75;

    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final ProviderTrustClient providerTrustClient;

    public EmploymentMatchBffService(WorkforceGovernanceClient workforceGovernanceClient,
                                     ProviderTrustClient providerTrustClient) {
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.providerTrustClient = providerTrustClient;
    }

    /**
     * Match the EC number for {@code healthId} and, when matched and
     * {@code providerId} (numeric id or providerPublicId) is present, assert
     * EMPLOYMENT_MATCHED trust on the provider registry.
     */
    public Map<String, Object> matchAndAssert(String healthId, String ecNumber, String providerId) {
        Map<String, Object> out = new LinkedHashMap<>();
        String maskedEc = maskEc(ecNumber);
        out.put("maskedEcNumber", maskedEc);

        if (ecNumber == null || ecNumber.isBlank()) {
            out.put("status", "INVALID_REQUEST");
            out.put("message", "ecNumber is required");
            return out;
        }

        Map<String, Object> matchRequest = new LinkedHashMap<>();
        matchRequest.put("ecNumber", ecNumber);
        if (healthId != null && !healthId.isBlank()) {
            matchRequest.put("healthId", healthId);
        }
        JsonNode match = workforceGovernanceClient.matchEmployment(matchRequest);
        if (match == null || match.isMissingNode()) {
            out.put("status", "UNAVAILABLE");
            out.put("sourceOfRecord", "workforce-governance-service (wgv_hsc_employment)");
            return out;
        }

        String matchStatus = match.path("matchStatus").asText(null);
        out.put("status", "OK");
        out.put("matchStatus", matchStatus);
        copyMatchDetails(match, out);

        boolean matched = "MATCHED_BOTH".equals(matchStatus) || "MATCHED_EMPLOYMENT_ONLY".equals(matchStatus);
        if (!matched) {
            return out;
        }

        if (providerId == null || providerId.isBlank()) {
            // Wave 1 never creates providers — surface guidance instead.
            out.put("providerCreationRequired", true);
            return out;
        }

        double confidence = "MATCHED_BOTH".equals(matchStatus)
                ? CONFIDENCE_MATCHED_BOTH
                : CONFIDENCE_EMPLOYMENT_ONLY;
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("type", EVIDENCE_TYPE_EC_MATCH);
        evidence.put("source", EVIDENCE_SOURCE_HSC_EMPLOYMENT);
        evidence.put("ref", maskedEc);
        evidence.put("confidence", confidence);
        Map<String, Object> trustBody = new LinkedHashMap<>();
        trustBody.put("targetLevel", TARGET_LEVEL_EMPLOYMENT_MATCHED);
        trustBody.put("evidence", evidence);
        trustBody.put("channel", CHANNEL_WORKFORCE_B);

        ProviderTrustClient.TrustCallResult trust = providerTrustClient.assertTrust(providerId, trustBody);
        out.put("trustAssertion", describeTrustResult(trust));
        return out;
    }

    private static Map<String, Object> describeTrustResult(ProviderTrustClient.TrustCallResult trust) {
        Map<String, Object> assertion = new LinkedHashMap<>();
        if (trust == null || (!trust.success() && trust.statusCode() == 0)) {
            assertion.put("status", "UNAVAILABLE");
            assertion.put("sourceOfRecord", "varapi-service (provider trust ledger)");
            return assertion;
        }
        if (trust.success()) {
            assertion.put("status", "ASSERTED");
            JsonNode body = trust.body();
            if (body != null) {
                putIfText(assertion, body, "providerPublicId");
                putIfText(assertion, body, "trustLevel");
                putIfText(assertion, body, "registryStatus");
            }
        } else {
            // e.g. 409 — VARAPI's downgrade guard or evidence rule refused the assertion.
            assertion.put("status", "REJECTED");
            assertion.put("statusCode", trust.statusCode());
        }
        assertion.put("sourceOfRecord", "varapi-service (provider trust ledger)");
        return assertion;
    }

    /** Copy governance match detail fields; defensively never copy any EC field. */
    private static void copyMatchDetails(JsonNode match, Map<String, Object> out) {
        putIfText(out, match, "employmentId");
        putIfText(out, match, "linkedHealthId");
        putIfText(out, match, "cadre");
        putIfText(out, match, "grade");
        putIfText(out, match, "employmentStatus");
        putIfText(out, match, "disciplinaryEmploymentStatus");
        putIfText(out, match, "verificationStatus");
        if (match.has("conflictingRecordCount")) {
            out.put("conflictingRecordCount", match.path("conflictingRecordCount").asInt());
        }
        if (match.has("postings") && match.path("postings").isArray()) {
            out.put("postings", match.path("postings"));
        }
    }

    private static void putIfText(Map<String, Object> out, JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isMissingNode() && !value.isNull()) {
            out.put(field, value.asText());
        }
    }

    /** First two characters + {@code ***}; the raw EC never leaves this service. */
    static String maskEc(String ecNumber) {
        if (ecNumber == null || ecNumber.isBlank()) {
            return null;
        }
        String trimmed = ecNumber.trim();
        return trimmed.length() <= 2 ? trimmed + "***" : trimmed.substring(0, 2) + "***";
    }
}
