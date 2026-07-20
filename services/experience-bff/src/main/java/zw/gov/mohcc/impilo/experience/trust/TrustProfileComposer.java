package zw.gov.mohcc.impilo.experience.trust;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.ProviderTrustClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composes the four-block doctrine trust profile for a person (IATG Wave-1, WS-D).
 *
 * <p>The BFF is a composition layer, never a source of truth: each block cites
 * its system of record and degrades <em>independently</em> to
 * {@code {"status":"UNAVAILABLE"}} when that downstream fails — one broken
 * service never poisons the other blocks, and nothing is ever fabricated.</p>
 *
 * <ul>
 *   <li><b>identity</b> — identity-assurance-service (LOA / assurance state);</li>
 *   <li><b>professional</b> — VARAPI provider registry trust ledger; the provider
 *       is resolved from the Health ID via the registry's by-health-id lookup, or
 *       taken from an explicit {@code providerId} override;</li>
 *   <li><b>employment</b> — workforce-governance (wgv_hsc_employment) summary;
 *       EC numbers are always masked;</li>
 *   <li><b>operational</b> — vashandi work-context read model (active assignments
 *       / check-in state).</li>
 * </ul>
 */
@Service
public class TrustProfileComposer {

    private static final Logger log = LoggerFactory.getLogger(TrustProfileComposer.class);

    static final String STATUS_OK = "OK";
    static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    static final String STATUS_NO_PROVIDER_LINKED = "NO_PROVIDER_LINKED";

    private final IdentityAssuranceServiceClient identityAssuranceClient;
    private final VarapiServiceClient varapiServiceClient;
    private final ProviderTrustClient providerTrustClient;
    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final VashandiServiceClient vashandiServiceClient;
    private final ObjectMapper objectMapper;

    public TrustProfileComposer(IdentityAssuranceServiceClient identityAssuranceClient,
                                VarapiServiceClient varapiServiceClient,
                                ProviderTrustClient providerTrustClient,
                                WorkforceGovernanceClient workforceGovernanceClient,
                                VashandiServiceClient vashandiServiceClient,
                                ObjectMapper objectMapper) {
        this.identityAssuranceClient = identityAssuranceClient;
        this.varapiServiceClient = varapiServiceClient;
        this.providerTrustClient = providerTrustClient;
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.vashandiServiceClient = vashandiServiceClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> compose(String healthId, String providerIdOverride) {
        Map<String, Object> profile = new LinkedHashMap<>();
        // PII Wave 0.3: do NOT echo the raw Health ID to the browser. The healthId
        // is used only server-side below (professional/employment/operational
        // lookups); the trust-profile UI never reads it back (TrustProfilePanel
        // renders the four blocks + generatedAt only), so the browser holds no HID.
        profile.put("generatedAt", Instant.now().toString());
        profile.put("identity", identityBlock());
        profile.put("professional", professionalBlock(healthId, providerIdOverride));
        profile.put("employment", employmentBlock(healthId));
        profile.put("operational", operationalBlock(healthId));
        return profile;
    }

    /**
     * Identity block — identity-assurance-service is caller-context based (trust
     * headers identify the actor); the assurance shown is the authenticated
     * caller's, which for the self-service trust profile is the same person.
     */
    private Map<String, Object> identityBlock() {
        Map<String, Object> block = sourced("identity-assurance-service");
        try {
            JsonNode assurance = identityAssuranceClient.getAssuranceStatus();
            if (assurance == null || assurance.isMissingNode()) {
                block.put("status", STATUS_UNAVAILABLE);
                return block;
            }
            block.put("status", STATUS_OK);
            block.put("assurance", assurance);
        } catch (Exception e) {
            log.warn("Trust profile: identity block unavailable: {}", e.getMessage());
            block.put("status", STATUS_UNAVAILABLE);
        }
        return block;
    }

    /**
     * Professional block — VARAPI trust ledger. Provider resolution uses the
     * registry's existing {@code by-health-id} lookup (via
     * {@link VarapiServiceClient#rawGet}) so a genuine 404 (person has no
     * provider registration) is distinguished from a registry outage.
     */
    private Map<String, Object> professionalBlock(String healthId, String providerIdOverride) {
        Map<String, Object> block = sourced("varapi-service (provider registry trust ledger)");
        String providerId = providerIdOverride;
        if (providerId == null || providerId.isBlank()) {
            try {
                ResponseEntity<String> response =
                        varapiServiceClient.rawGet("/v1/internal/providers/by-health-id/"
                                + URLEncoder.encode(healthId, StandardCharsets.UTF_8));
                providerId = extractProviderId(response.getBody());
            } catch (HttpClientErrorException.NotFound e) {
                block.put("status", STATUS_NO_PROVIDER_LINKED);
                block.put("providerCreationRequired", true);
                return block;
            } catch (Exception e) {
                log.warn("Trust profile: provider resolution unavailable: {}", e.getMessage());
                block.put("status", STATUS_UNAVAILABLE);
                return block;
            }
            if (providerId == null) {
                block.put("status", STATUS_NO_PROVIDER_LINKED);
                block.put("providerCreationRequired", true);
                return block;
            }
        }

        JsonNode trust = providerTrustClient.getTrust(providerId);
        if (trust == null || trust.isMissingNode()) {
            block.put("status", STATUS_UNAVAILABLE);
            block.put("providerId", providerId);
            return block;
        }
        block.put("status", STATUS_OK);
        block.put("providerId", providerId);
        putIfText(block, trust, "trustLevel");
        putIfText(block, trust, "registryStatus");
        putIfText(block, trust, "onboardingChannel");
        if (trust.has("attempts")) {
            block.put("attempts", trust.path("attempts"));
        }
        return block;
    }

    /** Employment block — governance HSC employment summary; EC numbers masked. */
    private Map<String, Object> employmentBlock(String healthId) {
        Map<String, Object> block = sourced("workforce-governance-service (wgv_hsc_employment)");
        try {
            JsonNode records = workforceGovernanceClient.getJson(
                    "/v1/internal/governance/hsc/employment-records/search?healthId="
                            + URLEncoder.encode(healthId, StandardCharsets.UTF_8));
            if (records == null || records.isMissingNode() || !records.isArray()) {
                block.put("status", STATUS_UNAVAILABLE);
                return block;
            }
            block.put("status", STATUS_OK);
            block.put("recordCount", records.size());
            List<Map<String, Object>> summaries = new ArrayList<>();
            for (JsonNode record : records) {
                summaries.add(summariseEmployment(record));
            }
            block.put("records", summaries);
        } catch (Exception e) {
            log.warn("Trust profile: employment block unavailable: {}", e.getMessage());
            block.put("status", STATUS_UNAVAILABLE);
        }
        return block;
    }

    /** Operational block — vashandi work-context (active assignments / check-in). */
    private Map<String, Object> operationalBlock(String healthId) {
        Map<String, Object> block = sourced("vashandi-workforce-service (work-context read model)");
        try {
            JsonNode workContext = vashandiServiceClient.fetchWorkContext(healthId);
            if (workContext == null || workContext.isMissingNode()) {
                block.put("status", STATUS_UNAVAILABLE);
                return block;
            }
            block.put("status", STATUS_OK);
            JsonNode assignments = workContext.path("assignments");
            if (assignments.isArray()) {
                block.put("activeAssignmentCount", assignments.size());
                block.put("assignments", assignments);
            }
            if (workContext.has("requiresContextChooser")) {
                block.put("requiresContextChooser", workContext.path("requiresContextChooser").asBoolean());
            }
            if (workContext.has("checkIn")) {
                block.put("checkIn", workContext.path("checkIn"));
            }
        } catch (Exception e) {
            log.warn("Trust profile: operational block unavailable: {}", e.getMessage());
            block.put("status", STATUS_UNAVAILABLE);
        }
        return block;
    }

    /** Summary of one wgv_hsc_employment row — the raw EC number never leaves governance. */
    private static Map<String, Object> summariseEmployment(JsonNode record) {
        Map<String, Object> summary = new LinkedHashMap<>();
        putIfText(summary, record, "employmentStatus");
        putIfText(summary, record, "cadre");
        putIfText(summary, record, "grade");
        putIfText(summary, record, "postTitle");
        putIfText(summary, record, "currentPostingProvince");
        putIfText(summary, record, "currentPostingDistrict");
        putIfText(summary, record, "currentPostingFacility");
        putIfText(summary, record, "disciplinaryEmploymentStatus");
        putIfText(summary, record, "verificationStatus");
        String ec = record.path("ecNumber").asText(null);
        if (ec != null && !ec.isBlank()) {
            summary.put("maskedEcNumber", EmploymentMatchBffService.maskEc(ec));
        }
        return summary;
    }

    private String extractProviderId(String body) {
        try {
            if (body == null || body.isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(body);
            JsonNode data = node.has("data") ? node.path("data") : node;
            for (String field : List.of("providerPublicId", "providerId", "id")) {
                String value = data.path(field).asText(null);
                if (value != null && !value.isBlank() && !"null".equals(value)) {
                    return value;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> sourced(String sourceOfRecord) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("sourceOfRecord", sourceOfRecord);
        return block;
    }

    private static void putIfText(Map<String, Object> out, JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isMissingNode() && !value.isNull()) {
            out.put(field, value.asText());
        }
    }
}
