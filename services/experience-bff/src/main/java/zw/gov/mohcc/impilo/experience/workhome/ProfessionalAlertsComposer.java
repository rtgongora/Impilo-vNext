package zw.gov.mohcc.impilo.experience.workhome;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-cutting professional-standing alerts (Phase E5) — licence expiry/suspension, outstanding
 * CPD, assignments awaiting acceptance. Unlike the family adapters (E4) this is not gated by
 * the active {@link WorkHomeFamily}: a facility manager and a clinician both need to know their
 * own licence is expiring, so {@link WorkHomeCompositionService} includes this section for
 * every composition rather than routing it through one family.
 *
 * <p>"Scope restriction", "acting appointment ending" and "regulator response due" from the E5
 * plan have no queryable real source wired to the BFF yet (VARAPI's outstanding-compliance
 * endpoint is keyed by a numeric provider id, a different id space from the
 * {@code providerPublicId} every other call here uses, and WGV assignments carry no
 * acting/ending-soon flag) — omitted honestly rather than approximated.</p>
 */
@Component
public class ProfessionalAlertsComposer {

    private static final Logger log = LoggerFactory.getLogger(ProfessionalAlertsComposer.class);
    public static final String SECTION_ID = "professional-alerts";
    private static final String TITLE = "Professional alerts";
    private static final int LICENCE_EXPIRY_WARNING_DAYS = 60;
    private static final int LICENCE_EXPIRY_URGENT_DAYS = 14;

    private final VarapiServiceClient varapiClient;
    private final WorkforceGovernanceClient governanceClient;

    public ProfessionalAlertsComposer(VarapiServiceClient varapiClient, WorkforceGovernanceClient governanceClient) {
        this.varapiClient = varapiClient;
        this.governanceClient = governanceClient;
    }

    public FanoutTask<WorkHomeSection> sectionTask(String actorHealthId) {
        return FanoutTask.of(SECTION_ID, () -> compose(actorHealthId));
    }

    /**
     * Standalone composition, independent of any active work context — used by
     * {@code ProfessionalAlertsController} (Phase F4) for /professional, which has no
     * {@code context_id} to re-prove and needs licence/CPD/assignment alerts regardless of
     * whether the person currently has a work session open at all.
     */
    public WorkHomeSection compose(String actorHealthId) {
        String providerPublicId = resolveProviderPublicId(actorHealthId);
        if (providerPublicId == null) {
            return WorkHomeSection.empty(SECTION_ID, TITLE, "No provider identity resolved for this actor");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(licenceAlerts(providerPublicId));
        items.addAll(cpdAlerts(providerPublicId));
        items.addAll(pendingAssignmentAlerts(providerPublicId));
        return WorkHomeSection.ok(SECTION_ID, TITLE, items, Map.of("total", items.size()));
    }

    private List<Map<String, Object>> licenceAlerts(String providerPublicId) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            JsonNode licences = varapiClient.getProviderLicenses(providerPublicId);
            LocalDate today = LocalDate.now();
            for (JsonNode l : WorkHomeJson.asNodeList(licences)) {
                String status = WorkHomeJson.text(l, "status", "UNKNOWN");
                String licenceType = WorkHomeJson.text(l, "licenseType", "Licence");
                if ("SUSPENDED".equalsIgnoreCase(status) || "REVOKED".equalsIgnoreCase(status)) {
                    out.add(licenceItem(l, licenceType, status,
                            licenceType + " is " + status.toLowerCase(java.util.Locale.ROOT), "URGENT"));
                    continue;
                }
                String validTo = WorkHomeJson.text(l, "validTo");
                if (validTo == null || !"ACTIVE".equalsIgnoreCase(status)) {
                    continue;
                }
                try {
                    long daysToExpiry = ChronoUnit.DAYS.between(today, LocalDate.parse(validTo));
                    if (daysToExpiry < 0) {
                        out.add(licenceItem(l, licenceType, "EXPIRED", licenceType + " expired on " + validTo, "URGENT"));
                    } else if (daysToExpiry <= LICENCE_EXPIRY_WARNING_DAYS) {
                        String priority = daysToExpiry <= LICENCE_EXPIRY_URGENT_DAYS ? "URGENT" : "HIGH";
                        out.add(licenceItem(l, licenceType, "EXPIRING",
                                licenceType + " expires " + validTo + " (" + daysToExpiry + " days)", priority));
                    }
                } catch (Exception parseFailure) {
                    log.debug("professional-alerts: could not parse licence validTo '{}': {}", validTo, parseFailure.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("professional-alerts licence composition failed: {}", e.getMessage());
        }
        return out;
    }

    private Map<String, Object> licenceItem(JsonNode l, String licenceType, String alertStatus, String description, String priority) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "licence:" + WorkHomeJson.text(l, "id", licenceType));
        item.put("kind", "LICENCE_ALERT");
        item.put("source", "varapi");
        item.put("title", licenceType + " — " + WorkHomeJson.text(l, "councilName", "regulatory council"));
        item.put("description", description);
        item.put("status", alertStatus);
        item.put("priority", priority);
        item.put("due_at", WorkHomeJson.text(l, "validTo"));
        item.put("href", "/professional");
        return item;
    }

    private List<Map<String, Object>> cpdAlerts(String providerPublicId) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            JsonNode cpd = varapiClient.getProviderCpdSummary(providerPublicId);
            if (cpd == null || cpd.isNull() || cpd.isMissingNode()) {
                return out;
            }
            boolean met = cpd.path("pointsRequirementMet").asBoolean(true);
            if (met) {
                return out;
            }
            int required = cpd.path("requiredPoints").asInt(0);
            int earned = cpd.path("earnedPoints").asInt(0);
            String endDate = WorkHomeJson.text(cpd, "endDate");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "cpd:" + WorkHomeJson.text(cpd, "id", providerPublicId));
            item.put("kind", "CPD_ALERT");
            item.put("source", "varapi");
            item.put("title", "CPD points outstanding");
            item.put("description", earned + " of " + required + " points earned"
                    + (endDate != null ? " — cycle ends " + endDate : ""));
            item.put("status", "OUTSTANDING");
            item.put("priority", "HIGH");
            item.put("due_at", endDate);
            item.put("href", "/professional");
            out.add(item);
        } catch (Exception e) {
            log.debug("professional-alerts CPD composition failed: {}", e.getMessage());
        }
        return out;
    }

    private List<Map<String, Object>> pendingAssignmentAlerts(String providerPublicId) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            JsonNode assignments = governanceClient.searchAssignments("PROVIDER", providerPublicId, "PENDING");
            for (JsonNode a : WorkHomeJson.asNodeList(assignments)) {
                Map<String, Object> item = new LinkedHashMap<>();
                String id = WorkHomeJson.text(a, "id");
                item.put("id", "assignment:" + id);
                item.put("kind", "ASSIGNMENT_ALERT");
                item.put("source", "workforce-governance");
                item.put("title", "Assignment awaiting your acceptance");
                item.put("description", "Role: " + WorkHomeJson.text(a, "roleDefinitionId", "unspecified"));
                // Not "PENDING": this assignment awaits THIS provider's own acceptance —
                // see WorkHomeBucketing's comment on the reverse-reading convention.
                item.put("status", "PENDING_MY_ACCEPTANCE");
                item.put("priority", "HIGH");
                item.put("href", "/professional");
                out.add(item);
            }
        } catch (Exception e) {
            log.debug("professional-alerts assignment composition failed: {}", e.getMessage());
        }
        return out;
    }

    /** Mirrors WorkContextResolutionService's private resolveProviderPublicId — same source, same fail-soft. */
    private String resolveProviderPublicId(String actorHealthId) {
        try {
            JsonNode provider = varapiClient.getProviderByHealthId(actorHealthId);
            if (provider == null || provider.isNull()) return null;
            String publicId = WorkHomeJson.text(provider, "providerPublicId");
            return publicId != null ? publicId : WorkHomeJson.text(provider, "providerId");
        } catch (Exception e) {
            log.debug("professional-alerts: provider lookup failed for {}: {}", actorHealthId, e.getMessage());
            return null;
        }
    }
}
