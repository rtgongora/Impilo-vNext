package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceProfile;
import zw.gov.mohcc.impilo.msikaflow.domain.VendorStatus;
import zw.gov.mohcc.impilo.msikaflow.integration.TusoClient;
import zw.gov.mohcc.impilo.msikaflow.integration.VarapiClient;
import zw.gov.mohcc.impilo.msikaflow.integration.VashandiClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.VendorProfileEntity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OF-B5 — the §4.3 six-precondition provider-fulfilment eligibility check,
 * evaluated at invitation issuance AND re-evaluated inside commitment step 4
 * (§11.3). Fail-closed throughout: a registry that cannot be reached yields
 * UNVERIFIED, and UNVERIFIED is never eligible — an unreachable registry never
 * self-authorizes a regulated fulfilment.
 *
 * <p>Vendor registry references are read from the vendor profile's
 * {@code coverage} JSON: {@code providerPublicId} (VARAPI), {@code tusoFacilityId}
 * (TUSO numeric id), {@code premisesFacilityId}/{@code storeId} (DURA UUIDs),
 * {@code vashandiProfileId} (VASHANDI workforce profile),
 * {@code zones} (coarse ndila zones), and {@code capabilities}
 * (flag map: dispensing / diagnostics / service_delivery / controlled_authority /
 * cold_chain).</p>
 */
@Service
public class EligibilityService {

    private static final Logger log = LoggerFactory.getLogger(EligibilityService.class);

    public static final String OUTCOME_PASS = "PASS";
    public static final String OUTCOME_FAIL = "FAIL";
    public static final String OUTCOME_UNVERIFIED = "UNVERIFIED";

    // Structured refusal codes (RC-6 family)
    public static final String CODE_PROVIDER_REF_MISSING = "PROVIDER_REF_MISSING";
    public static final String CODE_REGISTRY_UNREACHABLE = "REGISTRY_UNREACHABLE";
    public static final String CODE_LICENCE_NOT_ACTIVE = "LICENCE_NOT_ACTIVE";
    public static final String CODE_SCOPE_MISMATCH = "SCOPE_MISMATCH";
    public static final String CODE_PREMISES_REF_MISSING = "PREMISES_REF_MISSING";
    public static final String CODE_PREMISES_UNVERIFIED = "PREMISES_UNVERIFIED";
    public static final String CODE_PREMISES_NOT_OPERATIONAL = "PREMISES_NOT_OPERATIONAL";
    public static final String CODE_BINDING_UNVERIFIED = "BINDING_UNVERIFIED";
    public static final String CODE_BINDING_ABSENT = "BINDING_ABSENT";
    public static final String CODE_CAPABILITY_MISSING = "CAPABILITY_MISSING";
    public static final String CODE_CONTROLLED_AUTHORITY_MISSING = "CONTROLLED_AUTHORITY_MISSING";
    public static final String CODE_VENDOR_NOT_IN_GOOD_STANDING = "VENDOR_NOT_IN_GOOD_STANDING";

    public record PreconditionResult(int number, String name, String outcome, String code, String detail) {
        public boolean passed() {
            return OUTCOME_PASS.equals(outcome);
        }
    }

    public record EligibilityResult(boolean eligible, List<PreconditionResult> preconditions,
                                    OffsetDateTime evaluatedAt) {
        /** The first non-PASS refusal code, for structured surfacing. */
        public String firstRefusalCode() {
            return preconditions.stream()
                    .filter(p -> !p.passed())
                    .map(PreconditionResult::code)
                    .findFirst()
                    .orElse(null);
        }
    }

    private final VarapiClient varapiClient;
    private final TusoClient tusoClient;
    private final VashandiClient vashandiClient;
    private final ObjectMapper objectMapper;

    public EligibilityService(VarapiClient varapiClient, TusoClient tusoClient,
                              VashandiClient vashandiClient, ObjectMapper objectMapper) {
        this.varapiClient = varapiClient;
        this.tusoClient = tusoClient;
        this.vashandiClient = vashandiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluate the six preconditions for one vendor against one request.
     * Eligible only when ALL six PASS; UNVERIFIED fails closed.
     */
    public EligibilityResult evaluate(VendorProfileEntity vendor, MarketplaceRequestEntity request,
                                      HttpServletRequest inbound) {
        JsonNode coverage = parseCoverage(vendor);
        List<PreconditionResult> results = new ArrayList<>(6);

        results.add(checkProfessionalRegistration(coverage));
        results.add(checkScopeOfPractice(coverage, request.getProfile()));
        results.add(checkPremisesOperational(coverage));
        results.add(checkEmploymentBinding(coverage, inbound));
        results.add(checkCapabilityGrade(coverage, request));
        results.add(checkGoodStanding(vendor));

        boolean eligible = results.stream().allMatch(PreconditionResult::passed);
        return new EligibilityResult(eligible, List.copyOf(results), OffsetDateTime.now());
    }

    /** Direct capability probe used by the §13.4 controlled-gating filter. */
    public boolean hasControlledAuthority(VendorProfileEntity vendor) {
        return capabilityFlag(parseCoverage(vendor), "controlled_authority");
    }

    /** Serialises the evaluation for the invitation/offer audit snapshot. */
    public String toSnapshotJson(EligibilityResult result) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eligible", result.eligible());
        root.put("evaluatedAt", result.evaluatedAt().toString());
        ArrayNode arr = root.putArray("preconditions");
        for (PreconditionResult p : result.preconditions()) {
            ObjectNode n = arr.addObject();
            n.put("number", p.number());
            n.put("name", p.name());
            n.put("outcome", p.outcome());
            if (p.code() != null) n.put("code", p.code());
            if (p.detail() != null) n.put("detail", p.detail());
        }
        return root.toString();
    }

    // ── the six preconditions ────────────────────────────────────────────

    /** 1 — VARAPI: verified professional registration with an active licence. */
    private PreconditionResult checkProfessionalRegistration(JsonNode coverage) {
        String providerPublicId = text(coverage, "providerPublicId");
        if (providerPublicId == null) {
            return new PreconditionResult(1, "PROFESSIONAL_REGISTRATION", OUTCOME_FAIL,
                    CODE_PROVIDER_REF_MISSING, "vendor profile carries no VARAPI provider reference");
        }
        JsonNode standing = varapiClient.getStandingSummary(providerPublicId);
        if (standing == null || standing.isMissingNode() || standing.isNull()) {
            return new PreconditionResult(1, "PROFESSIONAL_REGISTRATION", OUTCOME_UNVERIFIED,
                    CODE_REGISTRY_UNREACHABLE, "VARAPI standing summary unavailable — fail closed");
        }
        boolean active = standing.path("active").asBoolean(false);
        boolean licensed = standing.path("hasValidLicense").asBoolean(false);
        if (active && licensed) {
            return new PreconditionResult(1, "PROFESSIONAL_REGISTRATION", OUTCOME_PASS, null, null);
        }
        return new PreconditionResult(1, "PROFESSIONAL_REGISTRATION", OUTCOME_FAIL,
                CODE_LICENCE_NOT_ACTIVE,
                "active=" + active + " hasValidLicense=" + licensed);
    }

    /** 2 — scope of practice matching the order's profile (licence class alone is not item authority). */
    private PreconditionResult checkScopeOfPractice(JsonNode coverage, MarketplaceProfile profile) {
        String requiredFlag = switch (profile) {
            case MEDICATION -> "dispensing";
            case DIAGNOSTICS -> "diagnostics";
            case SERVICE -> "service_delivery";
        };
        if (capabilityFlag(coverage, requiredFlag)) {
            return new PreconditionResult(2, "SCOPE_OF_PRACTICE", OUTCOME_PASS, null, null);
        }
        return new PreconditionResult(2, "SCOPE_OF_PRACTICE", OUTCOME_FAIL, CODE_SCOPE_MISMATCH,
                "vendor scope does not cover profile " + profile + " (missing '" + requiredFlag + "')");
    }

    /** 3 — TUSO: verified, currently operational premises of the required type. */
    private PreconditionResult checkPremisesOperational(JsonNode coverage) {
        long tusoFacilityId = coverage.path("tusoFacilityId").asLong(-1);
        if (tusoFacilityId <= 0) {
            return new PreconditionResult(3, "PREMISES_OPERATIONAL", OUTCOME_FAIL,
                    CODE_PREMISES_REF_MISSING, "vendor profile carries no TUSO facility reference");
        }
        JsonNode summary = tusoClient.getFacilityStatusSummary(tusoFacilityId);
        if (summary == null || summary.isMissingNode() || summary.isNull()) {
            return new PreconditionResult(3, "PREMISES_OPERATIONAL", OUTCOME_UNVERIFIED,
                    CODE_PREMISES_UNVERIFIED, "TUSO status summary unavailable — fail closed");
        }
        boolean operational = summary.path("operational").asBoolean(false);
        if (!operational) {
            String status = firstText(summary, "operationalStatus", "status");
            operational = status != null && (status.equalsIgnoreCase("OPERATIONAL")
                    || status.equalsIgnoreCase("ACTIVE"));
        }
        if (operational) {
            return new PreconditionResult(3, "PREMISES_OPERATIONAL", OUTCOME_PASS, null, null);
        }
        return new PreconditionResult(3, "PREMISES_OPERATIONAL", OUTCOME_FAIL,
                CODE_PREMISES_NOT_OPERATIONAL, "facility " + tusoFacilityId + " not operational");
    }

    /** 4 — VASHANDI: current employment/affiliation binding professional → premises. */
    private PreconditionResult checkEmploymentBinding(JsonNode coverage, HttpServletRequest inbound) {
        String profileRef = text(coverage, "vashandiProfileId");
        String facilityRef = text(coverage, "premisesFacilityId");
        if (profileRef == null || facilityRef == null) {
            return new PreconditionResult(4, "EMPLOYMENT_BINDING", OUTCOME_FAIL, CODE_BINDING_ABSENT,
                    "vendor profile carries no VASHANDI profile/premises references");
        }
        Optional<Boolean> bound = vashandiClient.hasActiveAssignment(profileRef, facilityRef, inbound);
        if (bound.isEmpty()) {
            return new PreconditionResult(4, "EMPLOYMENT_BINDING", OUTCOME_UNVERIFIED,
                    CODE_BINDING_UNVERIFIED, "VASHANDI registry unavailable — fail closed");
        }
        if (Boolean.TRUE.equals(bound.get())) {
            return new PreconditionResult(4, "EMPLOYMENT_BINDING", OUTCOME_PASS, null, null);
        }
        return new PreconditionResult(4, "EMPLOYMENT_BINDING", OUTCOME_FAIL, CODE_BINDING_ABSENT,
                "no active VASHANDI assignment binds the professional to the premises");
    }

    /** 5 — capability grade for order-specific constraints (controlled register, cold chain). */
    private PreconditionResult checkCapabilityGrade(JsonNode coverage, MarketplaceRequestEntity request) {
        if (request.isControlled() && !capabilityFlag(coverage, "controlled_authority")) {
            return new PreconditionResult(5, "CAPABILITY_GRADE", OUTCOME_FAIL,
                    CODE_CONTROLLED_AUTHORITY_MISSING,
                    "controlled lines require capability 'controlled_authority'");
        }
        if (requestNeedsColdChain(request) && !capabilityFlag(coverage, "cold_chain")) {
            return new PreconditionResult(5, "CAPABILITY_GRADE", OUTCOME_FAIL,
                    CODE_CAPABILITY_MISSING, "cold-chain lines require capability 'cold_chain'");
        }
        return new PreconditionResult(5, "CAPABILITY_GRADE", OUTCOME_PASS, null, null);
    }

    /** 6 — regulatory and marketplace good standing (not suspended/sanctioned/embargoed). */
    private PreconditionResult checkGoodStanding(VendorProfileEntity vendor) {
        VendorStatus status = vendor.getStatus();
        if (status == VendorStatus.ACTIVE || status == VendorStatus.APPROVED) {
            return new PreconditionResult(6, "GOOD_STANDING", OUTCOME_PASS, null, null);
        }
        return new PreconditionResult(6, "GOOD_STANDING", OUTCOME_FAIL,
                CODE_VENDOR_NOT_IN_GOOD_STANDING, "vendor status " + status);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private boolean requestNeedsColdChain(MarketplaceRequestEntity request) {
        try {
            JsonNode snapshot = objectMapper.readTree(request.getPublishedLinesJson());
            for (JsonNode line : snapshot.path("lines")) {
                if (line.path("coldChain").asBoolean(false)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse published snapshot for request {}: {}",
                    request.getRequestId(), e.getMessage());
        }
        return false;
    }

    private JsonNode parseCoverage(VendorProfileEntity vendor) {
        try {
            String coverage = vendor.getCoverage();
            if (coverage == null || coverage.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(coverage);
        } catch (Exception e) {
            log.warn("Unparseable coverage json for vendor {}: {}", vendor.getVendorId(), e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private static boolean capabilityFlag(JsonNode coverage, String flag) {
        return coverage.path("capabilities").path(flag).asBoolean(false);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isValueNode() && !v.asText().isBlank() ? v.asText() : null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String v = text(node, field);
            if (v != null) return v;
        }
        return null;
    }

    /** DURA source refs for reservation (OF-B11) — {@code premisesFacilityId}/{@code storeId}. */
    public record DuraSource(UUID facilityId, UUID storeId) {}

    /** Parses the vendor's DURA stock-source refs; empty when not configured (→ REPORTED grade / RC-3). */
    public Optional<DuraSource> duraSource(VendorProfileEntity vendor) {
        JsonNode coverage = parseCoverage(vendor);
        String facility = text(coverage, "premisesFacilityId");
        String store = text(coverage, "storeId");
        if (facility == null || store == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DuraSource(UUID.fromString(facility), UUID.fromString(store)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** True when the vendor's declared coverage zones include the request's coarse zone. */
    public boolean coversZone(VendorProfileEntity vendor, String coarseZone) {
        if (coarseZone == null || coarseZone.isBlank()) {
            return false;
        }
        JsonNode zones = parseCoverage(vendor).path("zones");
        if (!zones.isArray()) {
            return false;
        }
        for (JsonNode zone : zones) {
            if (coarseZone.equalsIgnoreCase(zone.asText())) {
                return true;
            }
        }
        return false;
    }

    /** The vendor's VARAPI provider reference (for the controlled-register write). */
    public String providerRef(VendorProfileEntity vendor) {
        return text(parseCoverage(vendor), "providerPublicId");
    }
}
