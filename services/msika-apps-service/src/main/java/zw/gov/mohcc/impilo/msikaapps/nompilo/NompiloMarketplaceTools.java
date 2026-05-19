package zw.gov.mohcc.impilo.msikaapps.nompilo;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msikaapps.api.dto.MsikaAppsDtos.*;
import zw.gov.mohcc.impilo.msikaapps.service.MarketplaceService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * NompiloMarketplaceTools — deterministic tool surface exposed to the Nompilo
 * intelligent companion for marketplace discovery, troubleshooting and
 * activation-status questions.
 *
 * <p>Every tool here is read-only / non-mutating and returns a small,
 * Nompilo-friendly map structure. Tools are role-, tenant- and facility-aware:
 * the calling layer must supply caller context, and TSHEPO is enforced upstream
 * at the controller boundary. These tools NEVER expose secrets, signing keys,
 * audit-only data categories or unapproved items.
 *
 * <p>Registered in the AI Skill manifest at
 * {@code contracts/ai-skills/nompilo-marketplace-helper.skill.json}.
 *
 * <p>The user-facing tool names are stable identifiers used by the LLM
 * orchestration layer; do not rename them without bumping the skill manifest
 * version and notifying the LLM orchestration owner.
 */
@Service
public class NompiloMarketplaceTools {

    private final MarketplaceService marketplace;

    public NompiloMarketplaceTools(MarketplaceService marketplace) {
        this.marketplace = marketplace;
    }

    /**
     * findMarketplaceCapability — Search the capability marketplace by type
     * and category. Used when a user asks "is there a telemedicine app?",
     * "show me LIMS connectors", "what AI skills are available?".
     */
    public List<Map<String, Object>> findMarketplaceCapability(String type, String category) {
        return marketplace.searchCatalogue(type, category, null).stream()
                .filter(i -> "APPROVED".equals(i.approvalStatus()))
                .map(this::toToolItem)
                .toList();
    }

    /**
     * explainCapabilityStatus — Explain to a user, in plain language, the
     * status of a specific marketplace item: approved, pending, suspended,
     * deprecated. Used when a user asks "why can't I use the X app?".
     */
    public Map<String, Object> explainCapabilityStatus(String itemCode) {
        try {
            MarketplaceItemResponse item = marketplace.getItem(itemCode);
            String explanation = switch (Optional.ofNullable(item.approvalStatus()).orElse("UNKNOWN")) {
                case "APPROVED"           -> item.name() + " is approved and can be requested or activated where authorised.";
                case "DRAFT", "SUBMITTED" -> item.name() + " is under publisher review and not yet available to request.";
                case "IN_REVIEW"          -> item.name() + " is under Health OS governance review.";
                case "REJECTED"           -> item.name() + " was reviewed and is not currently approved for the Health OS.";
                case "DEPRECATED"         -> item.name() + " has been deprecated and is being retired.";
                default                    -> item.name() + " has status " + item.approvalStatus() + ".";
            };
            return Map.of(
                    "itemCode", item.itemCode(),
                    "name", item.name(),
                    "type", item.type(),
                    "approvalStatus", Optional.ofNullable(item.approvalStatus()).orElse("UNKNOWN"),
                    "healthStatus", Optional.ofNullable(item.healthStatus()).orElse("UNKNOWN"),
                    "explanation", explanation,
                    "documentationUrl", Optional.ofNullable(item.documentationUrl()).orElse("")
            );
        } catch (IllegalArgumentException notFound) {
            return Map.of(
                    "itemCode", itemCode,
                    "explanation", "I could not find a capability with code " + itemCode + " in the marketplace.",
                    "found", false
            );
        }
    }

    /**
     * listInstalledCapabilities — Read-only view of what is installed and
     * active for the current tenant. Used when a user asks "what's installed
     * here?" or "which Health OS apps does my facility have?". Lifecycle is
     * optional ("ACTIVE", "INSTALLED", "SUSPENDED", "DEPRECATED").
     */
    public List<Map<String, Object>> listInstalledCapabilities(String tenantId, String lifecycle) {
        return marketplace.listInstallations(tenantId, lifecycle).stream()
                .map(i -> Map.<String, Object>of(
                        "installationId", i.id(),
                        "itemId", i.itemId(),
                        "itemVersion", i.itemVersion(),
                        "lifecycle", i.lifecycle(),
                        "healthStatus", Optional.ofNullable(i.healthStatus()).orElse("UNKNOWN"),
                        "facilities", i.facilityIds(),
                        "rolesEnabled", i.rolesEnabled()
                ))
                .toList();
    }

    /**
     * summarizePendingApprovals — Operations summary for admins: how many
     * activation requests are waiting on review. Used by admins asking
     * "what's in my approval queue?".
     */
    public Map<String, Object> summarizePendingApprovals(String tenantId) {
        List<ActivationRequestResponse> pending = marketplace.listActivationRequests(tenantId, "REQUESTED");
        List<ActivationRequestResponse> inReview = marketplace.listActivationRequests(tenantId, "IN_REVIEW");
        return Map.of(
                "tenantId", tenantId,
                "requested", pending.size(),
                "inReview", inReview.size(),
                "totalAwaitingDecision", pending.size() + inReview.size(),
                "explanation", (pending.size() + inReview.size()) == 0
                        ? "There are no activation requests awaiting your decision."
                        : "You have " + (pending.size() + inReview.size())
                                + " marketplace activation request(s) awaiting a decision."
        );
    }

    /**
     * troubleshootIntegration — High-level explainer for a citizen or provider
     * who sees a "temporarily unavailable" or "suspended" message. Does NOT
     * surface stack traces, credential failures, or internal hostnames.
     */
    public Map<String, Object> troubleshootIntegration(String itemCode) {
        try {
            MarketplaceItemResponse item = marketplace.getItem(itemCode);
            String guidance = switch (Optional.ofNullable(item.healthStatus()).orElse("UNKNOWN")) {
                case "HEALTHY"      -> "This capability is reported healthy. If you still see an error, please try again, then contact your facility administrator.";
                case "DEGRADED"     -> "This capability is currently degraded. It may respond more slowly than usual or fall back to a backup channel.";
                case "UNAVAILABLE"  -> "This capability is currently unavailable. The operations team has been notified.";
                default              -> "I do not have current health information for this capability. Please contact your administrator if the issue persists.";
            };
            return Map.of(
                    "itemCode", item.itemCode(),
                    "name", item.name(),
                    "healthStatus", Optional.ofNullable(item.healthStatus()).orElse("UNKNOWN"),
                    "guidance", guidance,
                    "supportContact", Optional.ofNullable(item.documentationUrl()).orElse("")
            );
        } catch (IllegalArgumentException notFound) {
            return Map.of(
                    "itemCode", itemCode,
                    "guidance", "I could not find a capability with code " + itemCode + " in the marketplace.",
                    "found", false
            );
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> toToolItem(MarketplaceItemResponse i) {
        return Map.of(
                "itemCode", i.itemCode(),
                "name", i.name(),
                "type", i.type(),
                "category", i.category(),
                "publisherName", Optional.ofNullable(i.publisherName()).orElse(""),
                "approvalStatus", Optional.ofNullable(i.approvalStatus()).orElse("UNKNOWN"),
                "healthStatus", Optional.ofNullable(i.healthStatus()).orElse("UNKNOWN"),
                "description", Optional.ofNullable(i.description()).orElse(""),
                "documentationUrl", Optional.ofNullable(i.documentationUrl()).orElse("")
        );
    }
}
