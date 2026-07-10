package zw.gov.mohcc.impilo.experience.trustconsole;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceAuditHelper;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceDtos;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernancePolicyService;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceService;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoFacilityClaimClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * IATG Trust Console composition (experience plane, orchestration only — no truth here).
 *
 * <p>Unifies the previously invisible IATG review queues into one governed surface:</p>
 * <ul>
 *   <li><b>provider-access-requests</b> — varapi review lane (SoR: varapi)</li>
 *   <li><b>facility-admin-appointments</b> — tuso cross-facility PENDING claims (SoR: tuso)</li>
 *   <li><b>org-access-requests</b> — admin-governance onboarding/access requests (BFF store +
 *       workforce-governance reconciliation); decisions reuse
 *       {@link AdminGovernanceService#decideAccessRequest} so approval fulfilment
 *       (Keycloak staging + invitation delivery) still fires on APPROVED</li>
 *   <li><b>assurance-upgrades</b> — identity-assurance-service upgrade queue (SoR: identity-assurance)</li>
 * </ul>
 *
 * <p><b>Degradation contract:</b> one dead downstream must NEVER 500 the console. Every
 * queue is fetched independently; a failed fetch reports {@code integrationStatus
 * "pending_backend"} for that queue only, using the established admin-governance
 * language, while the rest of the summary stays live.</p>
 *
 * <p><b>Trust:</b> every route is prechecked against the caller's session contract via
 * {@link AdminGovernancePolicyService} and audited via {@link AdminGovernanceAuditHelper},
 * mirroring the AdminGovernanceController conventions. ext_authz enforcement lives in
 * tshepo-authz V032 (SYSTEM_ADMIN / HIE_ADMIN).</p>
 */
@Service
public class TrustConsoleService {

    public static final String QUEUE_PROVIDER_ACCESS = "provider-access-requests";
    public static final String QUEUE_FACILITY_ADMIN = "facility-admin-appointments";
    public static final String QUEUE_ORG_ACCESS = "org-access-requests";
    public static final String QUEUE_ASSURANCE = "assurance-upgrades";
    public static final String QUEUE_INVITATIONS = "pending-invitations";

    public static final Set<String> QUEUES = Set.of(
            QUEUE_PROVIDER_ACCESS, QUEUE_FACILITY_ADMIN, QUEUE_ORG_ACCESS, QUEUE_ASSURANCE,
            QUEUE_INVITATIONS);

    private static final String ACTION_VIEW = "TRUST_CONSOLE_VIEW";
    private static final String ACTION_DECIDE = "ACCESS_REQUEST_DECIDE";
    private static final String SOURCE_PAGE = "/registry-admin/trust-console";

    private static final Logger log = LoggerFactory.getLogger(TrustConsoleService.class);

    private final SessionExperienceService sessionExperienceService;
    private final AdminGovernancePolicyService policyService;
    private final AdminGovernanceAuditHelper auditHelper;
    private final AdminGovernanceService adminGovernanceService;
    private final VarapiServiceClient varapiClient;
    private final TusoFacilityClaimClient tusoClient;
    private final IdentityAssuranceServiceClient identityAssuranceClient;
    private final zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceImportService importService;

    public TrustConsoleService(SessionExperienceService sessionExperienceService,
                               AdminGovernancePolicyService policyService,
                               AdminGovernanceAuditHelper auditHelper,
                               AdminGovernanceService adminGovernanceService,
                               VarapiServiceClient varapiClient,
                               TusoFacilityClaimClient tusoClient,
                               IdentityAssuranceServiceClient identityAssuranceClient,
                               zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceImportService importService) {
        this.sessionExperienceService = sessionExperienceService;
        this.policyService = policyService;
        this.auditHelper = auditHelper;
        this.adminGovernanceService = adminGovernanceService;
        this.varapiClient = varapiClient;
        this.tusoClient = tusoClient;
        this.identityAssuranceClient = identityAssuranceClient;
        this.importService = importService;
    }

    // ── Summary ──────────────────────────────────────────────────────────────────

    /** Per-queue counts with per-queue degradation. Never throws for a dead downstream. */
    public Map<String, Object> summary(String tenantId, String actorId, String providerId, boolean hasFacility) {
        AdminGovernanceDtos.PolicyDecision decision = precheck(actorId, providerId, hasFacility, ACTION_VIEW);
        if (!decision.allowed()) {
            return denied(decision);
        }
        Map<String, Object> queues = new LinkedHashMap<>();
        queues.put(QUEUE_PROVIDER_ACCESS, countSection(() -> arraySize(
                varapiClient.listProviderAccessRequestsForReview(null)),
                "Provider registry (varapi) is unavailable — pending provider access requests cannot be counted."));
        queues.put(QUEUE_FACILITY_ADMIN, countSection(() -> arraySize(
                tusoClient.appointmentsByState("PENDING")),
                "Facility registry (tuso) is unavailable — pending facility admin claims cannot be counted."));
        queues.put(QUEUE_ORG_ACCESS, countSection(() ->
                pendingOrgAccessRequests(tenantId, actorId).size(),
                "Admin governance access-request store is unavailable — pending organisation requests cannot be counted."));
        queues.put(QUEUE_ASSURANCE, countSection(() -> arraySize(
                identityAssuranceClient.listUpgradeRequests()),
                "Identity assurance service is unavailable — the upgrade queue cannot be counted."));
        queues.put(QUEUE_INVITATIONS, countSection(() -> pendingInvitationItems().size(),
                "Workforce governance is unavailable — pending onboarding invitations cannot be counted."));

        boolean allLive = queues.values().stream()
                .allMatch(q -> "live".equals(((Map<?, ?>) q).get("integrationStatus")));
        auditHelper.emit("trust_console.summary_viewed", actorId, null, Map.of("allLive", allLive));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "live");
        out.put("integrationStatus", allLive ? "live" : "partial");
        out.put("queues", queues);
        if (!allLive) {
            out.put("friendlyMessage",
                    "Some queues are degraded (pending_backend) — counts shown are honest partials, not zeros.");
        }
        return out;
    }

    // ── Queues ───────────────────────────────────────────────────────────────────

    public Map<String, Object> queue(String tenantId, String actorId, String providerId,
                                     boolean hasFacility, String queueName) {
        String queue = normalizeQueue(queueName);
        AdminGovernanceDtos.PolicyDecision decision = precheck(actorId, providerId, hasFacility, ACTION_VIEW);
        if (!decision.allowed()) {
            return denied(decision);
        }
        Map<String, Object> section = switch (queue) {
            case QUEUE_PROVIDER_ACCESS -> itemsSection(() ->
                    jsonItems(varapiClient.listProviderAccessRequestsForReview(null)),
                    "Provider registry (varapi) is unavailable — the provider access review queue cannot be loaded.");
            case QUEUE_FACILITY_ADMIN -> itemsSection(() ->
                    jsonItems(tusoClient.appointmentsByState("PENDING")),
                    "Facility registry (tuso) is unavailable — the facility admin claim queue cannot be loaded.");
            case QUEUE_ORG_ACCESS -> itemsSection(() ->
                    List.copyOf(pendingOrgAccessRequests(tenantId, actorId)),
                    "Admin governance access-request store is unavailable — the organisation queue cannot be loaded.");
            case QUEUE_ASSURANCE -> itemsSection(() ->
                    jsonItems(identityAssuranceClient.listUpgradeRequests()),
                    "Identity assurance service is unavailable — the upgrade queue cannot be loaded.");
            case QUEUE_INVITATIONS -> itemsSection(this::pendingInvitationItems,
                    "Workforce governance is unavailable — the invitation queue cannot be loaded.");
            default -> throw new IllegalArgumentException("Unknown trust-console queue: " + queueName);
        };
        auditHelper.emit("trust_console.queue_viewed", actorId, queue, Map.of("queue", queue));
        section.put("queue", queue);
        return section;
    }

    // ── Recent decisions ─────────────────────────────────────────────────────────

    /**
     * Recently decided items: varapi decided requests (APPROVED/REJECTED, newest first)
     * plus the admin-governance audit feed. Workforce-governance adjudication decisions
     * are subject-keyed only (no cross-subject recent list exists) and are therefore
     * honestly absent here rather than fabricated.
     */
    public Map<String, Object> recentDecisions(String tenantId, String actorId, String providerId,
                                               boolean hasFacility) {
        AdminGovernanceDtos.PolicyDecision decision = precheck(actorId, providerId, hasFacility, ACTION_VIEW);
        if (!decision.allowed()) {
            return denied(decision);
        }
        Map<String, Object> providerDecisions = itemsSection(() ->
                jsonItems(varapiClient.listProviderAccessRequestsForReview(List.of("APPROVED", "REJECTED"))),
                "Provider registry (varapi) is unavailable — decided provider access requests cannot be loaded.");
        Map<String, Object> auditEvents = itemsSection(() ->
                List.copyOf(adminGovernanceService.listAuditEvents(tenantId, actorId)),
                "Audit feed is unavailable — recent governance audit events cannot be loaded.");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "live");
        out.put("providerAccessDecisions", providerDecisions);
        out.put("governanceAuditEvents", auditEvents);
        return out;
    }

    // ── Decisions ────────────────────────────────────────────────────────────────

    /**
     * Decision passthrough per queue. The decision is prechecked (ACCESS_REQUEST_DECIDE),
     * audited, and forwarded to the owning system of record — the BFF never records a
     * decision of its own.
     */
    public Map<String, Object> decide(String tenantId, String actorId, String providerId,
                                      boolean hasFacility, String queueName, String itemId,
                                      String decisionValue, String note) {
        String queue = normalizeQueue(queueName);
        AdminGovernanceDtos.PolicyDecision decision = precheck(actorId, providerId, hasFacility, ACTION_DECIDE);
        if (!decision.allowed()) {
            return denied(decision);
        }
        String normalizedDecision = decisionValue == null ? "" : decisionValue.trim().toUpperCase(Locale.ROOT);
        Map<String, Object> result;
        try {
            result = switch (queue) {
                case QUEUE_PROVIDER_ACCESS -> decideProviderAccess(itemId, normalizedDecision, note);
                case QUEUE_FACILITY_ADMIN -> decideFacilityAdmin(actorId, itemId, normalizedDecision);
                case QUEUE_ORG_ACCESS -> decideOrgAccess(tenantId, actorId, providerId, hasFacility,
                        itemId, normalizedDecision, note);
                case QUEUE_ASSURANCE -> decideAssurance(itemId, normalizedDecision, note);
                case QUEUE_INVITATIONS -> decideInvitation(actorId, providerId, hasFacility,
                        itemId, normalizedDecision);
                default -> throw new IllegalArgumentException("Unknown trust-console queue: " + queueName);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Trust console decision failed for queue={} item={}: {}", queue, itemId, e.getMessage());
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("status", "pending_backend");
            failed.put("integrationStatus", "pending_backend");
            failed.put("friendlyMessage",
                    "The owning service did not accept the decision — nothing was recorded. Try again once it is reachable.");
            failed.put("detail", e.getMessage());
            auditHelper.emit("trust_console.decision_failed", actorId, itemId,
                    Map.of("queue", queue, "decision", normalizedDecision));
            return failed;
        }
        auditHelper.emit("trust_console.decision_recorded", actorId, itemId,
                Map.of("queue", queue, "decision", normalizedDecision));
        result.put("queue", queue);
        result.put("itemId", itemId);
        result.put("decision", normalizedDecision);
        return result;
    }

    /**
     * Pending onboarding invitations composed from workforce-governance import rows
     * (invitation lifecycle attached by AdminGovernanceImportService). Activated and
     * revoked invitations are excluded; sent/expired/failed remain actionable.
     */
    private List<Map<String, Object>> pendingInvitationItems() {
        var batchesEnvelope = importService.list(null);
        if (!"live".equals(batchesEnvelope.integrationStatus())) {
            throw new IllegalStateException(batchesEnvelope.friendlyMessage() == null
                    ? "Import history unavailable." : batchesEnvelope.friendlyMessage());
        }
        Object rawItems = batchesEnvelope.data() == null ? null : batchesEnvelope.data().get("items");
        if (!(rawItems instanceof List<?> batches)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int scanned = 0;
        for (Object rawBatch : batches) {
            if (scanned >= 10) break; // newest batches only — bounded fan-out
            if (!(rawBatch instanceof Map<?, ?> batch)) continue;
            Object batchId = batch.get("id");
            if (batchId == null) continue;
            scanned++;
            var rowsEnvelope = importService.listRows(batchId.toString());
            if (!"live".equals(rowsEnvelope.integrationStatus()) || rowsEnvelope.data() == null) continue;
            Object rawRows = rowsEnvelope.data().get("items");
            if (!(rawRows instanceof List<?> rows)) continue;
            for (Object rawRow : rows) {
                if (!(rawRow instanceof Map<?, ?> row)) continue;
                Object invitation = row.get("invitation");
                if (!(invitation instanceof Map<?, ?> inv)) continue;
                String status = inv.get("status") == null ? "" : inv.get("status").toString();
                if (!Set.of("sent", "expired", "failed").contains(status)) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                Object rowId = row.get("id");
                item.put("id", batchId + ":" + rowId);
                item.put("importBatchId", batchId.toString());
                item.put("rowId", rowId == null ? null : rowId.toString());
                item.put("invitation", inv);
                item.put("providerPublicId", row.get("providerPublicId"));
                item.put("matchedHealthId", row.get("matchedHealthId"));
                item.put("outcome", row.get("outcome"));
                item.put("executionStatus", row.get("executionStatus"));
                out.add(item);
            }
        }
        return out;
    }

    /** RESEND / REVOKE map to the established per-row invitation actions. */
    private Map<String, Object> decideInvitation(String actorId, String providerId,
                                                 boolean hasFacility, String itemId, String decision) {
        int sep = itemId.indexOf(':');
        if (sep <= 0 || sep == itemId.length() - 1) {
            throw new IllegalArgumentException(
                    "Invitation items are keyed importBatchId:rowId — got: " + itemId);
        }
        String importBatchId = itemId.substring(0, sep);
        String rowId = itemId.substring(sep + 1);
        var response = switch (decision) {
            case "RESEND" -> importService.resendRowInvitation(actorId, providerId, hasFacility,
                    importBatchId, rowId);
            case "REVOKE" -> importService.revokeRowInvitation(actorId, providerId, hasFacility,
                    importBatchId, rowId);
            default -> throw new IllegalArgumentException(
                    "Invitation decisions are RESEND or REVOKE — got: " + decision);
        };
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", response.status());
        out.put("integrationStatus", "pending_backend".equals(response.status()) ? "pending_backend" : "live");
        out.put("title", response.friendlyTitle());
        out.put("message", response.friendlyMessage());
        return out;
    }

    private Map<String, Object> decideProviderAccess(String publicId, String decision, String note) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        if (note != null && !note.isBlank()) {
            body.put("note", note.trim());
        }
        JsonNode result = varapiClient.decideProviderAccessRequest(publicId, body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "completed");
        out.put("integrationStatus", "live");
        out.put("result", result);
        return out;
    }

    private Map<String, Object> decideFacilityAdmin(String actorId, String appointmentId, String decision) {
        if (!"APPROVED".equals(decision) && !"APPROVE".equals(decision)) {
            // Honest boundary: tuso exposes approve only; rejection of a facility-admin
            // appointment is not implemented downstream and is never faked here.
            throw new IllegalArgumentException(
                    "Facility admin appointments support APPROVED only — tuso has no reject transition yet.");
        }
        JsonNode result = tusoClient.approve(appointmentId, Map.of("approvedBy", actorId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "completed");
        out.put("integrationStatus", "live");
        out.put("result", result);
        return out;
    }

    private Map<String, Object> decideOrgAccess(String tenantId, String actorId, String providerId,
                                                boolean hasFacility, String requestId,
                                                String decision, String note) {
        // Reuse the admin-governance decision seam so APPROVED still triggers onboarding
        // fulfilment (Keycloak staging + invitation delivery via GovernanceInvitationService).
        String mapped = switch (decision) {
            case "APPROVED", "APPROVE" -> "approve";
            case "REJECTED", "REJECT" -> "reject";
            case "NEEDS_MORE_INFORMATION" -> "request_more_information";
            default -> throw new IllegalArgumentException(
                    "Unsupported decision '" + decision + "' for organisation access requests");
        };
        AdminGovernanceDtos.ActionResponse response = adminGovernanceService.decideAccessRequest(
                tenantId, actorId, providerId, hasFacility, requestId,
                new AdminGovernanceDtos.AccessRequestDecisionRequest(mapped, note, null));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", response.status());
        out.put("integrationStatus", response.integrationStatus());
        out.put("result", response);
        return out;
    }

    private Map<String, Object> decideAssurance(String itemId, String decision, String note) {
        boolean approve = switch (decision) {
            case "APPROVED", "APPROVE" -> true;
            case "REJECTED", "REJECT" -> false;
            default -> throw new IllegalArgumentException(
                    "Unsupported decision '" + decision + "' for assurance upgrades — expected APPROVED or REJECTED");
        };
        long id;
        try {
            id = Long.parseLong(itemId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Assurance upgrade request id must be numeric: " + itemId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("approve", approve);
        body.put("reason", note != null && !note.isBlank() ? note.trim() : (approve ? "Approved" : "Rejected"));
        JsonNode result = identityAssuranceClient.decideUpgradeRequest(id, body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "completed");
        out.put("integrationStatus", "live");
        out.put("result", result);
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private List<AdminGovernanceDtos.AccessRequestRecord> pendingOrgAccessRequests(String tenantId, String actorId) {
        return adminGovernanceService.listAccessRequests(tenantId, actorId).stream()
                .filter(r -> Set.of("PENDING", "NEEDS_INFO", "ESCALATED").contains(r.status()))
                .toList();
    }

    private AdminGovernanceDtos.PolicyDecision precheck(String actorId, String providerId,
                                                        boolean hasFacility, String action) {
        Map<String, Object> contract = sessionExperienceService.buildExperienceContract(
                actorId, null, providerId, hasFacility);
        return policyService.evaluate(contract, new AdminGovernanceDtos.PrecheckRequest(
                action, SOURCE_PAGE, null, null, null, null, null, null, null, null, "MEDIUM", Map.of()));
    }

    private static Map<String, Object> denied(AdminGovernanceDtos.PolicyDecision decision) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "denied");
        out.put("integrationStatus", "live");
        out.put("friendlyMessage", decision.warnings().isEmpty()
                ? "You do not have authority for the Trust Console in your current scope."
                : decision.warnings().get(0));
        out.put("policyDecision", decision);
        return out;
    }

    /** One summary tile: an honest count or an honest pending_backend — never a silent zero. */
    private static Map<String, Object> countSection(CountSupplier counter, String degradedMessage) {
        Map<String, Object> section = new LinkedHashMap<>();
        try {
            section.put("pendingCount", counter.get());
            section.put("integrationStatus", "live");
        } catch (Exception e) {
            log.warn("Trust console count degraded: {}", e.getMessage());
            section.put("pendingCount", null);
            section.put("integrationStatus", "pending_backend");
            section.put("friendlyMessage", degradedMessage);
        }
        return section;
    }

    private static Map<String, Object> itemsSection(ItemsSupplier supplier, String degradedMessage) {
        Map<String, Object> section = new LinkedHashMap<>();
        try {
            List<?> items = supplier.get();
            section.put("items", items);
            section.put("count", items.size());
            section.put("integrationStatus", "live");
        } catch (Exception e) {
            log.warn("Trust console queue degraded: {}", e.getMessage());
            section.put("items", List.of());
            section.put("count", null);
            section.put("integrationStatus", "pending_backend");
            section.put("friendlyMessage", degradedMessage);
        }
        return section;
    }

    private static int arraySize(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalStateException("Downstream returned no payload");
        }
        return node.isArray() ? node.size() : node.path("items").size();
    }

    private static List<JsonNode> jsonItems(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalStateException("Downstream returned no payload");
        }
        JsonNode array = node.isArray() ? node : node.path("items");
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items;
    }

    private static String normalizeQueue(String queueName) {
        String queue = queueName == null ? "" : queueName.trim().toLowerCase(Locale.ROOT);
        if (!QUEUES.contains(queue)) {
            throw new IllegalArgumentException("Unknown trust-console queue: " + queueName);
        }
        return queue;
    }

    @FunctionalInterface
    private interface CountSupplier {
        int get();
    }

    @FunctionalInterface
    private interface ItemsSupplier {
        List<?> get();
    }
}
