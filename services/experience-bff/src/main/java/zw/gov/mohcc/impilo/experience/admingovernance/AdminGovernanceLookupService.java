package zw.gov.mohcc.impilo.experience.admingovernance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.util.*;

@Service
public class AdminGovernanceLookupService {

    private static final Map<String, String> COUNCIL_NAMES = Map.ofEntries(
            Map.entry("HPA", "Health Professions Authority"),
            Map.entry("MLCSC", "Medical Laboratories and Clinical Scientists Council"),
            Map.entry("MDPC", "Medical and Dental Practitioners Council"),
            Map.entry("AHPC", "Allied Health Practitioners Council"),
            Map.entry("PCZ", "Pharmacists Council"),
            Map.entry("NCZ", "Nurses Council"),
            Map.entry("EHPC", "Environmental Health Practitioners Council"),
            Map.entry("MRPC", "Medical Rehabilitation Practitioners Council"),
            Map.entry("NTC", "Natural Therapists Council")
    );

    private final VarapiServiceClient varapiServiceClient;
    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final ObjectMapper objectMapper;

    public AdminGovernanceLookupService(VarapiServiceClient varapiServiceClient,
                                        WorkforceGovernanceClient workforceGovernanceClient,
                                        ObjectMapper objectMapper) {
        this.varapiServiceClient = varapiServiceClient;
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.objectMapper = objectMapper;
    }

    public AdminGovernanceDtos.LookupEnvelope searchProviderWorkers(String q,
                                                                    String providerWorkerId,
                                                                    String healthId,
                                                                    String profession,
                                                                    String cadre,
                                                                    String council,
                                                                    String status,
                                                                    String facilityId,
                                                                    String organisationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (q != null && !q.isBlank()) body.put("query", q);
        if (providerWorkerId != null) body.put("providerId", providerWorkerId);
        if (healthId != null) body.put("healthId", healthId);
        if (profession != null) body.put("profession", profession);
        if (cadre != null) body.put("cadre", cadre);
        if (council != null) body.put("council", council);
        if (status != null) body.put("status", status);
        if (facilityId != null) body.put("facilityId", facilityId);
        if (organisationId != null) body.put("organisationId", organisationId);

        JsonNode results = varapiServiceClient.searchProviders(body);
        if (results == null || results.isNull()) {
            return unavailable("Provider/Worker lookup is not currently available; assignment cannot be completed until professional eligibility can be verified.");
        }
        return live(Map.of("items", objectMapper.convertValue(results, List.class), "lookupAvailable", true));
    }

    public AdminGovernanceDtos.LookupEnvelope providerEligibility(String providerWorkerId) {
        JsonNode provider = varapiServiceClient.getProvider(providerWorkerId);
        if (provider == null || provider.isNull()) {
            return unavailable("Provider/Worker lookup is not currently available; assignment cannot be completed until professional eligibility can be verified.");
        }
        JsonNode licenses = varapiServiceClient.getProviderLicenses(providerWorkerId);
        JsonNode affiliations = varapiServiceClient.getProviderCouncilAffiliations(providerWorkerId);
        JsonNode cpd = varapiServiceClient.getProviderCpdSummary(providerWorkerId);
        JsonNode assignments = workforceGovernanceClient.searchAssignments("PROVIDER", providerWorkerId, "ACTIVE");

        List<String> warnings = new ArrayList<>();
        List<String> blockedReasons = new ArrayList<>();
        Map<String, Object> councilStatusSummary = councilSummary(affiliations, warnings, blockedReasons);
        Map<String, Object> hscSummary = hscSummary(providerWorkerId, provider.path("linkedHealthId").asText(null), warnings, blockedReasons);

        Map<String, Object> eligibility = new LinkedHashMap<>();
        eligibility.put("providerWorkerId", providerWorkerId);
        eligibility.put("linkedHealthId", provider.path("linkedHealthId").asText(null));
        eligibility.put("fullName", provider.path("displayName").asText(provider.path("fullName").asText("")));
        eligibility.put("providerWorkerType", provider.path("providerType").asText(""));
        eligibility.put("profession", provider.path("profession").asText(""));
        eligibility.put("cadre", provider.path("cadre").asText(""));
        eligibility.put("licenceStatus", provider.path("licenceStatus").asText(""));
        eligibility.put("professionalStatus", provider.path("professionalStatus").asText(""));
        eligibility.put("scopeOfPractice", provider.path("scopeOfPractice").asText(""));
        eligibility.put("restrictions", listFromNode(licenses, "restrictions"));
        eligibility.put("sanctions", listFromNode(licenses, "sanctions"));
        eligibility.put("cpdComplianceSummary", cpd != null ? objectMapper.convertValue(cpd, Map.class) : Map.of());
        eligibility.put("trainingWarnings", trainingWarnings(cpd));
        eligibility.put("councilStatusSummary", councilStatusSummary);
        eligibility.put("hscEmploymentSummary", hscSummary);
        eligibility.put("activeAssignments", assignments != null ? objectMapper.convertValue(assignments, List.class) : List.of());
        eligibility.put("eligibilityWarnings", warnings);
        eligibility.put("blockedReasons", blockedReasons);
        eligibility.put("lookupAvailable", true);
        eligibility.put("integrationStatus", "live");

        return live(Map.of("eligibility", eligibility));
    }

    public AdminGovernanceDtos.LookupEnvelope searchHscEmployment(String providerWorkerId,
                                                                  String healthId,
                                                                  String employeeNumber,
                                                                  String facilityId,
                                                                  String province,
                                                                  String district,
                                                                  String status) {
        String query = "/v1/internal/governance/hsc/employment-records/search"
                + "?providerWorkerId=" + encode(providerWorkerId)
                + "&healthId=" + encode(healthId)
                + "&facilityId=" + encode(facilityId)
                + "&province=" + encode(province)
                + "&district=" + encode(district)
                + "&status=" + encode(status);
        JsonNode records = workforceGovernanceClient.getJson(query);
        if (records == null || records.isNull()) {
            return unavailable("HSC employment lookup is not currently available.");
        }
        return live(Map.of("items", objectMapper.convertValue(records, List.class), "lookupAvailable", true));
    }

    public AdminGovernanceDtos.LookupEnvelope getHscEmployment(String employmentRecordId) {
        JsonNode record = workforceGovernanceClient.getJson("/v1/internal/governance/hsc/employment-records/" + employmentRecordId);
        if (record == null || record.isNull()) {
            return unavailable("HSC employment record unavailable.");
        }
        return live(Map.of("employment", objectMapper.convertValue(record, Map.class)));
    }

    public AdminGovernanceDtos.LookupEnvelope professionalStatus(String providerWorkerId,
                                                                   String healthId,
                                                                   String council,
                                                                   String registrationNumber,
                                                                   String profession,
                                                                   String cadre) {
        String resolvedProviderId = providerWorkerId;
        if ((resolvedProviderId == null || resolvedProviderId.isBlank()) && healthId != null) {
            JsonNode byHealth = varapiServiceClient.getProviderByHealthId(healthId);
            if (byHealth != null && !byHealth.isNull()) {
                resolvedProviderId = byHealth.path("providerId").asText(byHealth.path("id").asText(null));
            }
        }
        if (resolvedProviderId == null || resolvedProviderId.isBlank()) {
            return unavailable("Council/HPA professional status lookup unavailable.");
        }
        JsonNode affiliations = varapiServiceClient.getProviderCouncilAffiliations(resolvedProviderId);
        JsonNode licenses = varapiServiceClient.getProviderLicenses(resolvedProviderId);
        if (affiliations == null && licenses == null) {
            return unavailable("Council/HPA professional status lookup unavailable.");
        }

        List<String> warnings = new ArrayList<>();
        List<String> blockedReasons = new ArrayList<>();
        Map<String, Object> councilBlock = councilSummary(affiliations, warnings, blockedReasons);
        if (council != null && !council.isBlank()) {
            String councilCode = council.toUpperCase(Locale.ROOT);
            if (!councilBlock.getOrDefault("councilCode", "").toString().equalsIgnoreCase(councilCode)) {
                warnings.add("Requested council " + councilCode + " does not match primary registration.");
            }
        }

        Map<String, Object> status = new LinkedHashMap<>(councilBlock);
        status.put("providerWorkerId", resolvedProviderId);
        status.put("registrationNumber", registrationNumber != null ? registrationNumber : councilBlock.get("registrationNumber"));
        status.put("profession", profession);
        status.put("cadre", cadre);
        status.put("warnings", warnings);
        status.put("blockedReasons", blockedReasons);
        status.put("policyDecision", Map.of("allowed", blockedReasons.isEmpty(), "warnings", warnings));
        status.put("lookupAvailable", true);

        return live(Map.of("professionalStatus", status));
    }

    private Map<String, Object> councilSummary(JsonNode affiliations, List<String> warnings, List<String> blockedReasons) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (affiliations == null || affiliations.isNull()) {
            warnings.add("Council registration data unavailable.");
            return summary;
        }
        JsonNode primary = affiliations.isArray() && affiliations.size() > 0 ? affiliations.get(0) : affiliations;
        String councilCode = primary.path("councilCode").asText(primary.path("council").asText(""));
        summary.put("councilCode", councilCode);
        summary.put("councilName", COUNCIL_NAMES.getOrDefault(councilCode.toUpperCase(Locale.ROOT), councilCode));
        summary.put("registrationNumber", primary.path("registrationNumber").asText(""));
        summary.put("registrationStatus", primary.path("registrationStatus").asText(""));
        summary.put("licenceStatus", primary.path("licenceStatus").asText(""));
        summary.put("restrictionStatus", primary.path("restrictionStatus").asText(""));
        summary.put("disciplinaryStatus", primary.path("disciplinaryStatus").asText(""));
        summary.put("scopeOfPractice", primary.path("scopeOfPractice").asText(""));
        summary.put("expiryDate", primary.path("expiryDate").asText(""));
        summary.put("lastVerifiedAt", primary.path("lastVerifiedAt").asText(""));
        summary.put("verificationSource", "VARAPI");

        String regStatus = summary.get("registrationStatus").toString().toLowerCase(Locale.ROOT);
        if (regStatus.contains("suspend") || regStatus.contains("revok")) {
            blockedReasons.add("Council registration is " + summary.get("registrationStatus"));
            warnings.add("Council suspension/revocation blocks relevant Work activation.");
        }
        return summary;
    }

    private Map<String, Object> hscSummary(String providerWorkerId, String healthId, List<String> warnings, List<String> blockedReasons) {
        AdminGovernanceDtos.LookupEnvelope envelope = searchHscEmployment(providerWorkerId, healthId, null, null, null, null, null);
        if (!"live".equals(envelope.integrationStatus())) {
            warnings.add("HSC employment lookup unavailable.");
            return Map.of("integrationStatus", "pending_backend");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) envelope.data().getOrDefault("items", List.of());
        if (items.isEmpty()) {
            return Map.of("employmentStatus", "NOT_FOUND");
        }
        Map<String, Object> primary = items.get(0);
        String employmentStatus = String.valueOf(primary.getOrDefault("employmentStatus", ""));
        String disciplinary = String.valueOf(primary.getOrDefault("disciplinaryEmploymentStatus", ""));
        if (employmentStatus.toLowerCase(Locale.ROOT).contains("suspend")
                || employmentStatus.toLowerCase(Locale.ROOT).contains("dismiss")
                || disciplinary.toLowerCase(Locale.ROOT).contains("suspend")
                || disciplinary.toLowerCase(Locale.ROOT).contains("dismiss")) {
            blockedReasons.add("HSC employment status blocks public-sector Work: " + employmentStatus);
            warnings.add("Facility manager cannot override HSC blocked status.");
        }
        return primary;
    }

    private static List<String> trainingWarnings(JsonNode cpd) {
        List<String> warnings = new ArrayList<>();
        if (cpd == null || cpd.isNull()) {
            warnings.add("Training/CPD summary unavailable.");
            return warnings;
        }
        if (cpd.path("mandatoryTrainingComplete").asBoolean(true) == false) {
            warnings.add("Mandatory training incomplete.");
        }
        if (cpd.path("telemedicineCertified").asBoolean(true) == false) {
            warnings.add("Telemedicine certification missing.");
        }
        if (cpd.path("digitalHealthTrainingComplete").asBoolean(true) == false) {
            warnings.add("Digital health training incomplete.");
        }
        return warnings;
    }

    private static List<String> listFromNode(JsonNode node, String field) {
        if (node == null || node.isNull()) return List.of();
        JsonNode value = node.path(field);
        if (value.isArray()) {
            List<String> out = new ArrayList<>();
            value.forEach(v -> out.add(v.asText()));
            return out;
        }
        return List.of();
    }

    private static AdminGovernanceDtos.LookupEnvelope unavailable(String message) {
        return new AdminGovernanceDtos.LookupEnvelope("pending_backend", message, Map.of("lookupAvailable", false));
    }

    private static AdminGovernanceDtos.LookupEnvelope live(Map<String, Object> data) {
        return new AdminGovernanceDtos.LookupEnvelope("live", null, data);
    }

    private static String encode(String value) {
        return value == null ? "" : value;
    }
}
