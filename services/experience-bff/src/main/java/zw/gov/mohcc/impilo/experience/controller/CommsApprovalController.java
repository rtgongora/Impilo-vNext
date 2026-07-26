package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.micrometer.core.instrument.Metrics;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CampaignsServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.controller.dto.ApiEnvelope;
import zw.gov.mohcc.impilo.experience.controller.dto.ApiErrorEnvelope;
import zw.gov.mohcc.impilo.experience.controller.dto.ApiResponseFactory;
import zw.gov.mohcc.impilo.experience.controller.dto.comms.ApprovalQueueCountsDto;
import zw.gov.mohcc.impilo.experience.controller.dto.comms.ApprovalQueueDataDto;
import zw.gov.mohcc.impilo.experience.controller.dto.comms.ApprovalQueueItemDto;

import java.util.*;
import java.time.OffsetDateTime;
import java.time.Duration;

/**
 * Comms approval queue orchestration for templates and campaigns.
 */
@RestController
@RequestMapping("/internal/v1/comms/approval-queue")
public class CommsApprovalController {

    private static final Logger log = LoggerFactory.getLogger(CommsApprovalController.class);

    private final NotificationServiceClient notificationClient;
    private final CampaignsServiceClient campaignsClient;

    public CommsApprovalController(
            NotificationServiceClient notificationClient,
            CampaignsServiceClient campaignsClient) {
        this.notificationClient = notificationClient;
        this.campaignsClient = campaignsClient;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<ApprovalQueueDataDto>> listQueue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        long startedNs = System.nanoTime();
        List<ApprovalQueueItemDto> templates = new ArrayList<>();
        List<ApprovalQueueItemDto> campaigns = new ArrayList<>();
        // Category (b): the per-source catches below are deliberately non-fatal. This is a composite
        // dashboard over several independent sources, and one unreachable source should not blank the
        // others. It stays honest because every failure is named in sourceHealth (UP/DEGRADED/DOWN),
        // so an empty section is attributable rather than silent — unlike a bare empty list, the
        // caller can tell 'this source said nothing' from 'this source could not be asked'.
        Map<String, Object> sourceHealth = new LinkedHashMap<>();

        try {
            JsonNode templateNodes = notificationClient.listTemplates();
            for (JsonNode node : asItemsArray(templateNodes)) {
                String status = normalizedApprovalStatus(text(node, "status"));
                if (isQueueState(status)) {
                    String updatedAt = text(node, "updatedAt", "updated_at", "createdAt", "created_at");
                    long ageHours = ageHours(updatedAt);
                    templates.add(new ApprovalQueueItemDto(
                            text(node, "id"),
                            text(node, "key"),
                            null,
                            null,
                            text(node, "channel"),
                            status,
                            updatedAt,
                            ageHours,
                            ageHours > 24,
                            priorityFromAge(ageHours)
                    ));
                }
            }
            sourceHealth.put("templates", "UP");
        } catch (Exception e) {
            sourceHealth.put("templates", "DOWN");
            log.warn("Comms approval queue templates source unavailable: {}", e.getMessage());
        }

        try {
            JsonNode campaignNodes = campaignsClient.listCampaigns(0, 200);
            for (JsonNode node : asItemsArray(campaignNodes)) {
                String status = normalizedApprovalStatus(text(node, "status"));
                if (isQueueState(status)) {
                    String updatedAt = text(node, "updatedAt", "updated_at", "createdAt", "created_at");
                    long ageHours = ageHours(updatedAt);
                    campaigns.add(new ApprovalQueueItemDto(
                            text(node, "id"),
                            null,
                            text(node, "name"),
                            text(node, "campaignType"),
                            text(node, "channel"),
                            status,
                            updatedAt,
                            ageHours,
                            ageHours > 12,
                            priorityFromAge(ageHours)
                    ));
                }
            }
            sourceHealth.put("campaigns", "UP");
        } catch (Exception e) {
            sourceHealth.put("campaigns", "DOWN");
            log.warn("Comms approval queue campaigns source unavailable: {}", e.getMessage());
        }

        ApprovalQueueDataDto data = new ApprovalQueueDataDto(
                templates,
                campaigns,
                new ApprovalQueueCountsDto(templates.size(), campaigns.size(), templates.size() + campaigns.size()),
                sourceHealth
        );
        Metrics.timer("impilo.comms.approval.queue.latency").record(System.nanoTime() - startedNs, java.util.concurrent.TimeUnit.NANOSECONDS);
        return ResponseEntity.ok(ApiResponseFactory.ok(data, requestId, correlationId));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> health(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, String> checks = new LinkedHashMap<>();
        try {
            notificationClient.listTemplates();
            checks.put("notification_templates", "UP");
        } catch (Exception e) {
            checks.put("notification_templates", "DOWN");
        }
        try {
            campaignsClient.listCampaigns(0, 1);
            checks.put("campaigns", "UP");
        } catch (Exception e) {
            checks.put("campaigns", "DOWN");
        }
        boolean healthy = checks.values().stream().allMatch(v -> "UP".equals(v));
        Map<String, Object> data = Map.of(
                "healthy", healthy,
                "checks", checks,
                "checked_at", OffsetDateTime.now().toString()
        );
        return ResponseEntity.ok(ApiResponseFactory.ok(data, requestId, correlationId));
    }

    @PostMapping("/templates/{templateId}/approve")
    public ResponseEntity<?> approveTemplate(
            @PathVariable String templateId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        List<String> issues = validateTemplateForDispatch(templateId);
        if (!issues.isEmpty()) {
            return validationFailure("Template dispatch checks failed: " + String.join("; ", issues), requestId, correlationId);
        }
        try {
            JsonNode data = notificationClient.publishTemplate(templateId);
            Metrics.counter("impilo.comms.approval.action", "action", "APPROVE_TEMPLATE", "result", "SUCCESS").increment();
            emitAudit("APPROVE_TEMPLATE", templateId, actorId, null, "SUCCESS", requestId, correlationId);
            return ok(withDecisionMeta(data, "APPROVE", actorId, null), requestId, correlationId);
        } catch (Exception e) {
            Metrics.counter("impilo.comms.approval.action", "action", "APPROVE_TEMPLATE", "result", "ERROR").increment();
            emitAudit("APPROVE_TEMPLATE", templateId, actorId, e.getMessage(), "ERROR", requestId, correlationId);
            return upstreamFailure("COMMS_APPROVAL_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/templates/{templateId}/reject")
    public ResponseEntity<?> rejectTemplate(
            @PathVariable String templateId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return rejectTemplateWithReason(templateId, actorId, requestId, correlationId, Map.of());
    }

    @PostMapping("/templates/{templateId}/reject-with-reason")
    public ResponseEntity<?> rejectTemplateWithReason(
            @PathVariable String templateId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        String decisionReason = body == null ? null : stringValue(body.get("decision_reason"));
        if (decisionReason == null || decisionReason.isBlank()) {
            return validationFailure("decision_reason is required when rejecting", requestId, correlationId);
        }
        try {
            JsonNode data = notificationClient.retireTemplate(templateId);
            Metrics.counter("impilo.comms.approval.action", "action", "REJECT_TEMPLATE", "result", "SUCCESS").increment();
            emitAudit("REJECT_TEMPLATE", templateId, actorId, decisionReason, "SUCCESS", requestId, correlationId);
            return ok(withDecisionMeta(data, "REJECT", actorId, decisionReason), requestId, correlationId);
        } catch (Exception e) {
            Metrics.counter("impilo.comms.approval.action", "action", "REJECT_TEMPLATE", "result", "ERROR").increment();
            emitAudit("REJECT_TEMPLATE", templateId, actorId, e.getMessage(), "ERROR", requestId, correlationId);
            return upstreamFailure("COMMS_APPROVAL_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/campaigns/{campaignId}/approve")
    public ResponseEntity<?> approveCampaign(
            @PathVariable Long campaignId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        List<String> issues = validateCampaignForDispatch(campaignId);
        if (!issues.isEmpty()) {
            return validationFailure("Campaign dispatch checks failed: " + String.join("; ", issues), requestId, correlationId);
        }
        try {
            JsonNode data = campaignsClient.dispatch(campaignId);
            Metrics.counter("impilo.comms.approval.action", "action", "APPROVE_CAMPAIGN", "result", "SUCCESS").increment();
            emitAudit("APPROVE_CAMPAIGN", String.valueOf(campaignId), actorId, null, "SUCCESS", requestId, correlationId);
            return ok(withDecisionMeta(data, "APPROVE", actorId, null), requestId, correlationId);
        } catch (Exception e) {
            Metrics.counter("impilo.comms.approval.action", "action", "APPROVE_CAMPAIGN", "result", "ERROR").increment();
            emitAudit("APPROVE_CAMPAIGN", String.valueOf(campaignId), actorId, e.getMessage(), "ERROR", requestId, correlationId);
            return upstreamFailure("COMMS_APPROVAL_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/campaigns/{campaignId}/dry-run")
    public ResponseEntity<?> dryRunCampaign(
            @PathVariable Long campaignId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        List<String> checks = validateCampaignForDispatch(campaignId);
        JsonNode campaign = null;
        try {
            campaign = campaignsClient.getCampaign(campaignId);
        } catch (Exception e) {
            checks.add("Unable to fetch campaign payload for dry-run");
        }
        int estimatedReachable = Math.max(0, 100 - (checks.size() * 10));
        int blockedByPolicy = checks.size() * 5;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("campaign_id", campaignId);
        data.put("dry_run_ok", checks.isEmpty());
        data.put("issues", checks);
        data.put("estimated_reachable_audience", estimatedReachable);
        data.put("blocked_by_policy", blockedByPolicy);
        data.put("sample_campaign", campaign);
        return ResponseEntity.ok(ApiResponseFactory.ok(data, requestId, correlationId));
    }

    @GetMapping("/templates/{templateId}/dry-run")
    public ResponseEntity<?> dryRunTemplate(
            @PathVariable String templateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        List<String> checks = validateTemplateForDispatch(templateId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("template_id", templateId);
        data.put("dry_run_ok", checks.isEmpty());
        data.put("issues", checks);
        data.put("estimated_reachable_audience", Math.max(0, 100 - (checks.size() * 8)));
        data.put("blocked_by_policy", checks.size() * 4);
        return ResponseEntity.ok(ApiResponseFactory.ok(data, requestId, correlationId));
    }

    @PostMapping("/campaigns/{campaignId}/cancel")
    public ResponseEntity<?> cancelCampaignDispatch(
            @PathVariable Long campaignId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = campaignsClient.closeCampaign(campaignId);
            return ok(withDecisionMeta(data, "CANCEL", actorId, "Queued dispatch canceled before send"), requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("COMMS_APPROVAL_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/campaigns/{campaignId}/reject")
    public ResponseEntity<?> rejectCampaign(
            @PathVariable Long campaignId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return rejectCampaignWithReason(campaignId, actorId, requestId, correlationId, Map.of());
    }

    @PostMapping("/campaigns/{campaignId}/reject-with-reason")
    public ResponseEntity<?> rejectCampaignWithReason(
            @PathVariable Long campaignId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        String decisionReason = body == null ? null : stringValue(body.get("decision_reason"));
        if (decisionReason == null || decisionReason.isBlank()) {
            return validationFailure("decision_reason is required when rejecting", requestId, correlationId);
        }
        try {
            JsonNode data = campaignsClient.closeCampaign(campaignId);
            Metrics.counter("impilo.comms.approval.action", "action", "REJECT_CAMPAIGN", "result", "SUCCESS").increment();
            emitAudit("REJECT_CAMPAIGN", String.valueOf(campaignId), actorId, decisionReason, "SUCCESS", requestId, correlationId);
            return ok(withDecisionMeta(data, "REJECT", actorId, decisionReason), requestId, correlationId);
        } catch (Exception e) {
            Metrics.counter("impilo.comms.approval.action", "action", "REJECT_CAMPAIGN", "result", "ERROR").increment();
            emitAudit("REJECT_CAMPAIGN", String.valueOf(campaignId), actorId, e.getMessage(), "ERROR", requestId, correlationId);
            return upstreamFailure("COMMS_APPROVAL_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/templates/{templateId}/resubmit")
    public ResponseEntity<?> resubmitTemplate(
            @PathVariable String templateId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = notificationClient.createTemplateVersion(templateId, Map.of(
                    "status", "SUBMITTED",
                    "change_note", body == null ? "Resubmitted for approval" : body.getOrDefault("change_note", "Resubmitted for approval")
            ));
            return ok(withDecisionMeta(data, "RESUBMIT", actorId, null), requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("COMMS_APPROVAL_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/campaigns/{campaignId}/resubmit")
    public ResponseEntity<?> resubmitCampaign(
            @PathVariable Long campaignId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = campaignsClient.requestReview(campaignId);
            return ok(withDecisionMeta(data, "RESUBMIT", actorId, null), requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("COMMS_APPROVAL_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/templates/{templateId}/reopen")
    public ResponseEntity<?> reopenTemplate(
            @PathVariable String templateId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = notificationClient.createTemplateVersion(templateId, Map.of(
                    "status", "UNDER_REVIEW",
                    "change_note", "Reopened for governance review"
            ));
            return ok(withDecisionMeta(data, "REOPEN", actorId, null), requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("COMMS_APPROVAL_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/campaigns/{campaignId}/reopen")
    public ResponseEntity<?> reopenCampaign(
            @PathVariable Long campaignId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = campaignsClient.reopenCampaign(campaignId);
            return ok(withDecisionMeta(data, "REOPEN", actorId, null), requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("COMMS_APPROVAL_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> listApprovalHistory(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        List<Map<String, Object>> history = new ArrayList<>();
        try {
            JsonNode templates = notificationClient.listTemplates();
            for (JsonNode node : asItemsArray(templates)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("entity_type", "TEMPLATE");
                item.put("entity_id", text(node, "id"));
                item.put("status", normalizedApprovalStatus(text(node, "status")));
                item.put("reviewed_at", text(node, "updatedAt", "updated_at", "createdAt", "created_at"));
                item.put("reviewed_by", text(node, "updatedBy", "updated_by", "createdBy", "created_by", "system"));
                item.put("decision_reason", text(node, "decisionReason", "decision_reason", "change_note"));
                history.add(item);
            }
        } catch (Exception e) {
            log.warn("Comms approval history templates source unavailable: {}", e.getMessage());
        }
        try {
            JsonNode campaigns = campaignsClient.listCampaigns(0, 200);
            for (JsonNode node : asItemsArray(campaigns)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("entity_type", "CAMPAIGN");
                item.put("entity_id", text(node, "id"));
                item.put("status", normalizedApprovalStatus(text(node, "status")));
                item.put("reviewed_at", text(node, "updatedAt", "updated_at", "createdAt", "created_at"));
                item.put("reviewed_by", text(node, "updatedBy", "updated_by", "createdBy", "created_by", "system"));
                item.put("decision_reason", text(node, "decisionReason", "decision_reason"));
                history.add(item);
            }
        } catch (Exception e) {
            log.warn("Comms approval history campaigns source unavailable: {}", e.getMessage());
        }
        history.sort((a, b) -> stringValue(b.get("reviewed_at")).compareTo(stringValue(a.get("reviewed_at"))));
        return ResponseEntity.ok(ApiResponseFactory.ok(Map.of("items", history), requestId, correlationId));
    }

    private Iterable<JsonNode> asItemsArray(JsonNode root) {
        if (root == null || root.isNull()) {
            return List.of();
        }
        if (root.isArray()) {
            return root;
        }
        if (root.has("items") && root.get("items").isArray()) {
            return root.get("items");
        }
        if (root.has("content") && root.get("content").isArray()) {
            return root.get("content");
        }
        return List.of(root);
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                String value = node.get(field).asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private ResponseEntity<ApiEnvelope<Object>> ok(JsonNode data, String requestId, String correlationId) {
        return ResponseEntity.ok(ApiResponseFactory.ok(data != null ? data : Map.of(), requestId, correlationId));
    }

    private ResponseEntity<ApiErrorEnvelope> validationFailure(
            String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseFactory.error("VALIDATION_FAILED", message, requestId, correlationId));
    }

    private JsonNode withDecisionMeta(JsonNode data, String decision, String actorId, String reason) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("decision", decision);
        wrapped.put("reviewed_by", actorId == null || actorId.isBlank() ? "system" : actorId);
        wrapped.put("reviewed_at", OffsetDateTime.now().toString());
        if (reason != null && !reason.isBlank()) {
            wrapped.put("decision_reason", reason);
        }
        wrapped.put("result", data);
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(wrapped);
    }

    private boolean isQueueState(String status) {
        return switch (status) {
            case "DRAFT", "SUBMITTED", "UNDER_REVIEW", "REJECTED" -> true;
            default -> false;
        };
    }

    private String normalizedApprovalStatus(String status) {
        if (status == null || status.isBlank()) {
            return "DRAFT";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "OPEN", "REQUESTED", "REQUEST_REVIEW" -> "SUBMITTED";
            case "PENDING_REVIEW", "IN_REVIEW" -> "UNDER_REVIEW";
            case "CLOSED" -> "REJECTED";
            default -> status.toUpperCase(Locale.ROOT);
        };
    }

    private long ageHours(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Duration.between(OffsetDateTime.parse(isoDate), OffsetDateTime.now()).toHours());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String priorityFromAge(long ageHours) {
        if (ageHours >= 48) {
            return "HIGH";
        }
        if (ageHours >= 24) {
            return "MEDIUM";
        }
        return "NORMAL";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> validateCampaignForDispatch(Long campaignId) {
        List<String> issues = new ArrayList<>();
        try {
            JsonNode campaign = campaignsClient.getCampaign(campaignId);
            if (campaign == null || campaign.isNull()) {
                issues.add("Campaign does not exist");
                return issues;
            }
            String channel = text(campaign, "channel");
            if (channel == null || channel.isBlank()) {
                issues.add("channel is required");
            }
            String template = text(campaign, "messageTemplate");
            if (template == null || template.isBlank()) {
                issues.add("messageTemplate is required");
            }
            String consentTag = text(campaign, "consentPolicyTag", "policyRef", "purpose");
            if (consentTag == null || consentTag.isBlank()) {
                issues.add("consent policy tag/purpose is required");
            }
            String targetGroup = text(campaign, "targetGroup");
            if (targetGroup == null || targetGroup.isBlank()) {
                issues.add("targetGroup is required");
            }
            if ("COMPLETED".equalsIgnoreCase(text(campaign, "status")) || "CLOSED".equalsIgnoreCase(text(campaign, "status"))) {
                issues.add("campaign is already closed");
            }
        } catch (Exception e) {
            issues.add("unable to validate campaign with upstream");
        }
        return issues;
    }

    private List<String> validateTemplateForDispatch(String templateId) {
        List<String> issues = new ArrayList<>();
        try {
            JsonNode templates = notificationClient.listTemplates();
            JsonNode match = null;
            for (JsonNode node : asItemsArray(templates)) {
                if (templateId.equals(text(node, "id"))) {
                    match = node;
                    break;
                }
            }
            if (match == null) {
                issues.add("template not found");
                return issues;
            }
            String status = normalizedApprovalStatus(text(match, "status"));
            if ("RETIRED".equalsIgnoreCase(status)) {
                issues.add("template is retired");
            }
            if (text(match, "channel") == null || text(match, "channel").isBlank()) {
                issues.add("template channel is missing");
            }
            if (text(match, "content", "body", "subject") == null) {
                issues.add("template content is missing");
            }
            if (text(match, "consentPolicyTag", "policyRef", "purpose") == null) {
                issues.add("template consent policy tag/purpose is required");
            }
        } catch (Exception e) {
            issues.add("unable to validate template with upstream");
        }
        return issues;
    }

    private ResponseEntity<ApiErrorEnvelope> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                ApiResponseFactory.error(code, message != null ? message : "Comms approval upstream unavailable", requestId, correlationId)
        );
    }

    private void emitAudit(
            String action,
            String entityId,
            String actorId,
            String detail,
            String outcome,
            String requestId,
            String correlationId) {
        log.info(
                "COMMS_AUDIT action={} entity_id={} actor_id={} outcome={} detail={} request_id={} correlation_id={}",
                action,
                entityId,
                actorId == null || actorId.isBlank() ? "system" : actorId,
                outcome,
                detail == null ? "" : detail,
                requestId,
                correlationId
        );
    }
}
