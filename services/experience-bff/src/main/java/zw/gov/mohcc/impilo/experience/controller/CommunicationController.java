package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.Metrics;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CampaignsServiceClient;
import zw.gov.mohcc.impilo.experience.client.ChannelsServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.controller.dto.ApiEnvelope;
import zw.gov.mohcc.impilo.experience.controller.dto.ApiResponseFactory;
import zw.gov.mohcc.impilo.experience.controller.dto.comms.RoleDashboardDto;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineCommsMetricsStore;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineWorkflowTelemetry;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import zw.gov.mohcc.impilo.experience.resilience.UpstreamSourceCircuitBreaker;
import java.util.concurrent.TimeUnit;

/**
 * Communication noticeboard — announcements, clinical pages, and messages.
 * Orchestrates persisted data from campaigns-service, support-service, and channels-service.
 */
@RestController
@RequestMapping("/internal/v1/communication")
public class CommunicationController {

    private static final Logger log = LoggerFactory.getLogger(CommunicationController.class);

    private final CampaignsServiceClient campaignsClient;
    private final SupportServiceClient supportClient;
    private final ChannelsServiceClient channelsClient;
    private final NotificationServiceClient notificationClient;
    private final TelemedicineCommsMetricsStore telemedicineCommsMetricsStore;
    private final TelemedicineWorkflowTelemetry telemedicineWorkflowTelemetry;
    private final TshepoAuditServiceClient auditClient;
    private final UpstreamSourceCircuitBreaker circuitBreaker;

    private static final String TELEMEDICINE_WORKFLOW_EVENT_TYPE = "TELEMEDICINE_WORKFLOW";

    public CommunicationController(
            CampaignsServiceClient campaignsClient,
            SupportServiceClient supportClient,
            ChannelsServiceClient channelsClient,
            NotificationServiceClient notificationClient,
            TelemedicineCommsMetricsStore telemedicineCommsMetricsStore,
            TelemedicineWorkflowTelemetry telemedicineWorkflowTelemetry,
            TshepoAuditServiceClient auditClient,
            UpstreamSourceCircuitBreaker circuitBreaker) {
        this.campaignsClient = campaignsClient;
        this.supportClient = supportClient;
        this.channelsClient = channelsClient;
        this.notificationClient = notificationClient;
        this.telemedicineCommsMetricsStore = telemedicineCommsMetricsStore;
        this.telemedicineWorkflowTelemetry = telemedicineWorkflowTelemetry;
        this.auditClient = auditClient;
        this.circuitBreaker = circuitBreaker;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiEnvelope<RoleDashboardDto>> dashboard(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        long startedNs = System.nanoTime();
        Map<String, Object> sourceHealth = new LinkedHashMap<>();

        int activeAnnouncements = 0;
        int openPages = 0;
        int activeThreads = 0;
        int sentToday = 0;
        int failedToday = 0;

        if (isInCooldown("campaigns")) {
            sourceHealth.put("campaigns", "DEGRADED");
            Metrics.counter("impilo.communication.dashboard.source_skipped", "source", "campaigns").increment();
        } else try {
            long sourceStart = System.nanoTime();
            JsonNode campaigns = campaignsClient.listCampaigns(0, 100);
            for (JsonNode item : asItemsArray(campaigns)) {
                String type = text(item, "campaignType");
                String status = text(item, "status");
                if (type != null && type.toUpperCase(Locale.ROOT).contains("ANNOUNCEMENT")
                        && !"COMPLETED".equalsIgnoreCase(status)) {
                    activeAnnouncements++;
                }
            }
            sourceHealth.put("campaigns", "UP");
            clearCooldown("campaigns");
            Metrics.timer("impilo.communication.dashboard.source_latency", "source", "campaigns").record(System.nanoTime() - sourceStart, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            sourceHealth.put("campaigns", "DOWN");
            markFailure("campaigns");
            Metrics.counter("impilo.communication.dashboard.source_errors", "source", "campaigns").increment();
            log.warn("Communication dashboard campaigns source unavailable: {}", e.getMessage());
        }

        if (isInCooldown("support")) {
            sourceHealth.put("support", "DEGRADED");
            Metrics.counter("impilo.communication.dashboard.source_skipped", "source", "support").increment();
        } else try {
            long sourceStart = System.nanoTime();
            JsonNode pages = supportClient.listTickets("OPEN", null, "CLINICAL_PAGE", null, 0, 100);
            for (JsonNode ignored : asItemsArray(pages)) {
                openPages++;
            }
            sourceHealth.put("support", "UP");
            clearCooldown("support");
            Metrics.timer("impilo.communication.dashboard.source_latency", "source", "support").record(System.nanoTime() - sourceStart, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            sourceHealth.put("support", "DOWN");
            markFailure("support");
            Metrics.counter("impilo.communication.dashboard.source_errors", "source", "support").increment();
            log.warn("Communication dashboard support source unavailable: {}", e.getMessage());
        }

        if (isInCooldown("channels")) {
            sourceHealth.put("channels", "DEGRADED");
            Metrics.counter("impilo.communication.dashboard.source_skipped", "source", "channels").increment();
        } else try {
            long sourceStart = System.nanoTime();
            JsonNode sessions = channelsClient.listSessions();
            for (JsonNode item : asItemsArray(sessions)) {
                if (!"CLOSED".equalsIgnoreCase(text(item, "status"))) {
                    activeThreads++;
                }
            }
            sourceHealth.put("channels", "UP");
            clearCooldown("channels");
            Metrics.timer("impilo.communication.dashboard.source_latency", "source", "channels").record(System.nanoTime() - sourceStart, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            sourceHealth.put("channels", "DOWN");
            markFailure("channels");
            Metrics.counter("impilo.communication.dashboard.source_errors", "source", "channels").increment();
            log.warn("Communication dashboard channels source unavailable: {}", e.getMessage());
        }

        if (isInCooldown("notifications")) {
            sourceHealth.put("notifications", "DEGRADED");
            Metrics.counter("impilo.communication.dashboard.source_skipped", "source", "notifications").increment();
        } else try {
            long sourceStart = System.nanoTime();
            JsonNode notifications = notificationClient.listNotifications(0, 200);
            for (JsonNode item : asItemsArray(notifications)) {
                if (isToday(text(item, "createdAt", "created_at"))) {
                    if ("FAILED".equalsIgnoreCase(text(item, "status"))) {
                        failedToday++;
                    } else if ("SENT".equalsIgnoreCase(text(item, "status"))) {
                        sentToday++;
                    }
                }
            }
            sourceHealth.put("notifications", "UP");
            clearCooldown("notifications");
            Metrics.timer("impilo.communication.dashboard.source_latency", "source", "notifications").record(System.nanoTime() - sourceStart, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            sourceHealth.put("notifications", "DOWN");
            markFailure("notifications");
            Metrics.counter("impilo.communication.dashboard.source_errors", "source", "notifications").increment();
            log.warn("Communication dashboard notifications source unavailable: {}", e.getMessage());
        }

        boolean partialData = sourceHealth.values().stream().anyMatch(v -> !"UP".equalsIgnoreCase(String.valueOf(v)));
        Map<String, Object> telemedicineMetrics = telemedicineCommsMetricsStore.snapshot();
        Map<String, Object> executive = Map.of(
                "total_active_communications", activeAnnouncements + activeThreads + openPages,
                "delivery_success_rate", sentToday + failedToday == 0 ? 1.0 : ((double) sentToday / (double) (sentToday + failedToday)),
                "telemedicine_join_success_rate", telemedicineMetrics.getOrDefault("join_success_rate", 0D)
        );
        Map<String, Object> operations = new LinkedHashMap<>();
        operations.put("active_threads", activeThreads);
        operations.put("messages_sent_today", sentToday);
        operations.put("delivery_failures_today", failedToday);
        operations.put("trend_window", "24h");
        operations.put("telemedicine", telemedicineMetrics);
        Map<String, Object> clinical = Map.of(
                "open_clinical_pages", openPages,
                "escalation_candidates", failedToday
        );
        Map<String, Object> communications = new LinkedHashMap<>();
        communications.put("active_announcements", activeAnnouncements);
        communications.put("sent_today", sentToday);
        communications.put("failed_today", failedToday);
        communications.put("delta_pct", 0.0);
        communications.put("telemedicine_metrics", telemedicineMetrics);
        Map<String, Object> governance = new LinkedHashMap<>();
        governance.put("source_health", sourceHealth);
        governance.put("partial_data", partialData);
        governance.put("sample_size", Map.of(
                "campaigns", 100,
                "support_tickets", 100,
                "notifications", 200
        ));
        governance.put("telemedicine_operational_console", telemedicineWorkflowTelemetry.operationalSnapshot(telemedicineMetrics));
        governance.put("last_refreshed_at", OffsetDateTime.now().toString());
        RoleDashboardDto dashboard = new RoleDashboardDto(executive, operations, clinical, communications, governance);
        Metrics.timer("impilo.communication.dashboard.latency").record(System.nanoTime() - startedNs, TimeUnit.NANOSECONDS);
        return ResponseEntity.ok(ApiResponseFactory.ok(dashboard, requestId, correlationId));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> health(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, String> checks = new LinkedHashMap<>();
        try {
            notificationClient.unreadCount();
            checks.put("notifications_unread", "UP");
        } catch (Exception e) {
            checks.put("notifications_unread", "DOWN");
        }
        try {
            channelsClient.listSessions();
            checks.put("channels_sessions", "UP");
        } catch (Exception e) {
            checks.put("channels_sessions", "DOWN");
        }
        boolean healthy = checks.values().stream().allMatch("UP"::equals);
        return ResponseEntity.ok(ApiResponseFactory.ok(
                Map.of("healthy", healthy, "checks", checks, "checked_at", OffsetDateTime.now().toString()),
                requestId,
                correlationId
        ));
    }

    @GetMapping("/telemedicine/operations")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> telemedicineOperationalConsole(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> metrics = telemedicineCommsMetricsStore.snapshot();
        Map<String, Object> operations = new LinkedHashMap<>();
        operations.put("metrics", metrics);
        operations.put("console", telemedicineWorkflowTelemetry.operationalSnapshot(metrics));
        operations.put("last_refreshed_at", OffsetDateTime.now().toString());
        return ResponseEntity.ok(ApiResponseFactory.ok(operations, requestId, correlationId));
    }

    @GetMapping("/telemedicine/workflow-history")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> telemedicineWorkflowHistory(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "session_id", required = false) String sessionId,
            @RequestParam(name = "event_type", required = false) String eventType,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        OffsetDateTime fromTs = parseDate(from);
        OffsetDateTime toTs = parseDate(to);
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", queryTelemedicineWorkflowHistory(sessionId, boundedLimit, eventType, action, fromTs, toTs));
        data.put("session_id", sessionId);
        data.put("event_type", eventType);
        data.put("action", action);
        data.put("from", fromTs == null ? null : fromTs.toString());
        data.put("to", toTs == null ? null : toTs.toString());
        data.put("limit", boundedLimit);
        return ResponseEntity.ok(ApiResponseFactory.ok(data, requestId, correlationId));
    }

    @PostMapping("/telemedicine/workflow-history/retention")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> updateTelemedicineWorkflowRetention(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        Integer maxHistory = asPositiveInteger(body == null ? null : body.get("max_history"));
        Integer retentionHours = asPositiveInteger(body == null ? null : body.get("retention_hours"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retention", telemedicineWorkflowTelemetry.updateRetention(maxHistory, retentionHours));
        data.put("updated_at", OffsetDateTime.now().toString());
        return ResponseEntity.ok(ApiResponseFactory.ok(data, requestId, correlationId));
    }

    @PostMapping("/telemedicine/workflow-history/prune")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> pruneTelemedicineWorkflowHistory(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        int removed = telemedicineWorkflowTelemetry.pruneExpired();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("removed", removed);
        data.put("retention", telemedicineWorkflowTelemetry.retentionConfig());
        data.put("pruned_at", OffsetDateTime.now().toString());
        return ResponseEntity.ok(ApiResponseFactory.ok(data, requestId, correlationId));
    }

    @GetMapping("/telemedicine/workflow-history/export")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> exportTelemedicineWorkflowHistory(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "session_id", required = false) String sessionId,
            @RequestParam(name = "event_type", required = false) String eventType,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "limit", defaultValue = "500") int limit) {
        OffsetDateTime fromTs = parseDate(from);
        OffsetDateTime toTs = parseDate(to);
        List<Map<String, Object>> items = queryTelemedicineWorkflowHistory(
                sessionId,
                Math.max(1, Math.min(limit, 500)),
                eventType,
                action,
                fromTs,
                toTs
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("count", items.size());
        data.put("exported_at", OffsetDateTime.now().toString());
        data.put("format", "application/json");
        return ResponseEntity.ok(ApiResponseFactory.ok(data, requestId, correlationId));
    }

    // ── Announcements ───────────────────────────────────────────────

    @GetMapping("/announcements")
    public ResponseEntity<Map<String, Object>> listAnnouncements(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        try {
            JsonNode result = campaignsClient.listCampaigns(page, size);
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode campaign : asItemsArray(result)) {
                if (category == null || category.isBlank()
                        || campaign.path("campaignType").asText("").toLowerCase(Locale.ROOT).contains(category.toLowerCase(Locale.ROOT))) {
                    items.add(toAnnouncement(campaign));
                }
            }
            return ResponseEntity.ok(Map.of(
                    "data", items,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Announcements list unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> getAnnouncement(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = campaignsClient.getCampaign(id.getMostSignificantBits() & Long.MAX_VALUE);
            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "ANNOUNCEMENT_NOT_FOUND", "message", "Announcement not found"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return ResponseEntity.ok(Map.of(
                    "data", toAnnouncement(result),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Announcement fetch unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/announcements")
    public ResponseEntity<Map<String, Object>> createAnnouncement(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> create = new LinkedHashMap<>();
            create.put("name", body.getOrDefault("title", "Communication announcement"));
            create.put("description", body.getOrDefault("body", body.getOrDefault("description", "")));
            create.put("campaignType", body.getOrDefault("category", "ANNOUNCEMENT"));
            create.put("targetGroup", "{\"audience\":\"all\"}");
            create.put("messageTemplate", body.getOrDefault("body", body.getOrDefault("messageTemplate", "")));
            create.put("channel", body.getOrDefault("channel", "IN_APP"));
            JsonNode result = campaignsClient.createCampaign(create);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", result != null ? toAnnouncement(result) : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Announcement creation failed: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/announcements/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAnnouncement(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(Map.of(
                    "acknowledged", true,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Announcement acknowledge unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> archiveAnnouncement(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            campaignsClient.closeCampaign(id.getMostSignificantBits() & Long.MAX_VALUE);
            return ResponseEntity.ok(Map.of(
                    "archived", true,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Announcement archive unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    // ── Clinical Pages ──────────────────────────────────────────────

    @GetMapping("/pages")
    public ResponseEntity<Map<String, Object>> listPages(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String recipientId) {
        try {
            JsonNode result = supportClient.listTickets("OPEN", null, "CLINICAL_PAGE", recipientId, 0, 100);
            List<Map<String, Object>> pages = new ArrayList<>();
            for (JsonNode item : asItemsArray(result)) {
                pages.add(toClinicalPage(item));
            }
            return ResponseEntity.ok(Map.of(
                    "data", pages,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Pages list unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/pages")
    public ResponseEntity<Map<String, Object>> sendPage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> create = new LinkedHashMap<>();
            create.put("title", "Clinical page: " + body.getOrDefault("recipientName", body.getOrDefault("recipientId", "unknown")));
            create.put("description", body.getOrDefault("message", ""));
            create.put("reporterRef", body.getOrDefault("senderId", "system"));
            create.put("category", "CLINICAL_PAGE");
            create.put("priority", body.getOrDefault("urgency", "NORMAL"));
            create.put("facilityRef", body.getOrDefault("facilityId", null));
            JsonNode result = supportClient.createTicket(create);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", result != null ? toClinicalPage(result) : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Page send failed: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/pages/{id}/read")
    public ResponseEntity<Map<String, Object>> markPageRead(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            supportClient.updateTicket(id, Map.of("status", "IN_PROGRESS"));
            return ResponseEntity.ok(Map.of(
                    "status", "READ",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Page mark-read unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/pages/{id}/respond")
    public ResponseEntity<Map<String, Object>> respondToPage(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            supportClient.updateTicket(id, Map.of("status", "RESOLVED", "resolution", "Responded by clinician"));
            return ResponseEntity.ok(Map.of(
                    "status", "RESPONDED",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Page respond unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    // ── Clinical Messages ───────────────────────────────────────────

    @GetMapping("/messages/channels")
    public ResponseEntity<Map<String, Object>> listChannels(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = channelsClient.listSessions();
            List<Map<String, Object>> channels = new ArrayList<>();
            for (JsonNode item : asItemsArray(result)) {
                channels.add(toChannel(item));
            }
            return ResponseEntity.ok(Map.of(
                    "data", channels,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Message channels list unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/messages/channels/{channelId}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @PathVariable UUID channelId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            JsonNode result = channelsClient.listMessagesForSession(channelId);
            List<Map<String, Object>> messages = new ArrayList<>();
            for (JsonNode item : asItemsArray(result)) {
                messages.add(toMessage(item));
            }
            return ResponseEntity.ok(Map.of(
                    "data", messages,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Messages list unavailable: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("sessionId", body.get("channelId"));
            request.put("contentType", "text/plain");
            request.put("payloadJson", body.getOrDefault("content", ""));
            JsonNode result = channelsClient.sendOutboundMessage(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", result != null ? toMessage(result) : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Message send failed: {}", e.getMessage());
            return upstreamFailure("COMMUNICATION_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Communication upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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

    private Map<String, Object> toAnnouncement(JsonNode campaign) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", text(campaign, "id"));
        out.put("title", text(campaign, "name"));
        out.put("body", text(campaign, "description", "messageTemplate"));
        out.put("category", text(campaign, "campaignType"));
        out.put("status", text(campaign, "status"));
        out.put("published_at", text(campaign, "createdAt"));
        return out;
    }

    private Map<String, Object> toClinicalPage(JsonNode ticket) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", text(ticket, "ticketId", "id"));
        out.put("sender_id", text(ticket, "reporterRef"));
        out.put("sender_name", text(ticket, "reporterRef"));
        out.put("recipient_id", text(ticket, "assigneeRef"));
        out.put("recipient_name", text(ticket, "assigneeRef"));
        out.put("urgency", toLower(text(ticket, "priority"), "normal"));
        out.put("message", text(ticket, "description"));
        out.put("callback_number", null);
        out.put("status", toPageStatus(text(ticket, "status")));
        out.put("sent_at", text(ticket, "createdAt"));
        out.put("read_at", text(ticket, "updatedAt"));
        out.put("responded_at", text(ticket, "resolvedAt"));
        return out;
    }

    private Map<String, Object> toChannel(JsonNode session) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", text(session, "id"));
        out.put("name", text(session, "subjectRef"));
        out.put("channel_type", text(session, "channelType"));
        out.put("participants", text(session, "subjectRef", "agentId"));
        out.put("last_message_at", text(session, "lastActivity"));
        out.put("created_at", text(session, "startedAt"));
        Map<String, Object> telemedicineLinks = buildTelemedicineLinkage(session);
        if (!telemedicineLinks.isEmpty()) {
            out.put("telemedicine_links", telemedicineLinks);
        }
        return out;
    }

    private Map<String, Object> toMessage(JsonNode message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", text(message, "id"));
        out.put("channel_id", text(message, "sessionId"));
        String direction = text(message, "direction");
        out.put("sender_id", "INBOUND".equalsIgnoreCase(direction) ? "external" : "current-user");
        out.put("sender_name", "INBOUND".equalsIgnoreCase(direction) ? "External sender" : "You");
        out.put("content", text(message, "payloadJson", "content", "deliveryStatus"));
        out.put("message_type", text(message, "contentType", "messageType"));
        out.put("is_read", true);
        out.put("created_at", text(message, "createdAt"));
        Map<String, Object> telemedicineLinks = buildTelemedicineLinkage(message);
        if (!telemedicineLinks.isEmpty()) {
            out.put("telemedicine_links", telemedicineLinks);
        }
        return out;
    }

    private Map<String, Object> buildTelemedicineLinkage(JsonNode node) {
        Map<String, Object> links = new LinkedHashMap<>();
        putIfPresent(links, "client", text(node, "patientCpid", "patientId", "patient_id"));
        putIfPresent(links, "provider", text(node, "providerId", "provider_id", "agentId"));
        putIfPresent(links, "facility", text(node, "facilityId", "facility_id"));
        putIfPresent(links, "session", text(node, "sessionId", "session_id", "id"));
        putIfPresent(links, "appointment", text(node, "appointmentId", "appointment_id"));
        putIfPresent(links, "referral", text(node, "referralId", "referral_id"));
        putIfPresent(links, "prescription", text(node, "prescriptionId", "prescription_id"));
        putIfPresent(links, "lab_request", text(node, "labRequestId", "lab_request_id"));
        putIfPresent(links, "payment_or_claim", text(node, "paymentId", "claimId", "billingId"));
        putIfPresent(links, "helpdesk_ticket", text(node, "ticketId", "supportTicketId"));
        putIfPresent(links, "followup_task", text(node, "taskId", "followupTaskId"));
        return links;
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private String toLower(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String toPageStatus(String supportStatus) {
        if (supportStatus == null || supportStatus.isBlank()) {
            return "SENT";
        }
        return switch (supportStatus.toUpperCase(Locale.ROOT)) {
            case "IN_PROGRESS" -> "READ";
            case "RESOLVED", "CLOSED" -> "RESPONDED";
            default -> "SENT";
        };
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

    private boolean isToday(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isBlank()) {
            return false;
        }
        try {
            return OffsetDateTime.parse(isoDateTime).toLocalDate().isEqual(LocalDate.now());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isInCooldown(String source) {
        return circuitBreaker.isInCooldown(source);
    }

    private void markFailure(String source) {
        circuitBreaker.markFailure(source);
    }

    private void clearCooldown(String source) {
        circuitBreaker.clearCooldown(source);
    }

    private Integer asPositiveInteger(Object value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private OffsetDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Telemedicine workflow history is owned by the TSHEPO audit sovereign (the BFF holds no datasource).
     * Query it back and map each immutable audit event to the same row shape the in-memory projection used,
     * preserving the {@code session_id}/{@code action}/{@code event_type}/time-window filters.
     */
    private List<Map<String, Object>> queryTelemedicineWorkflowHistory(
            String sessionId, int limit, String eventType, String action,
            OffsetDateTime from, OffsetDateTime to) {
        String subjectRef = sessionId != null && !sessionId.isBlank()
                ? "telemedicine-session:" + sessionId : null;
        String fromTime = from == null ? null : from.toString();
        String toTime = to == null ? null : to.toString();
        try {
            JsonNode data = auditClient.queryEvents(
                    null, subjectRef, TELEMEDICINE_WORKFLOW_EVENT_TYPE, fromTime, toTime, 0, limit);
            return mapTelemedicineWorkflowEvents(data, eventType, action, limit);
        } catch (Exception e) {
            log.debug("Telemedicine workflow history query failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> mapTelemedicineWorkflowEvents(
            JsonNode data, String eventTypeFilter, String actionFilter, int limit) {
        if (data == null || data.isNull()) {
            return List.of();
        }
        JsonNode items = data.has("items") ? data.path("items") : data;
        if (!items.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode ev : items) {
            JsonNode detail = ev.path("detail");
            String workflowEvent = detail.path("workflow_event").asText(ev.path("eventType").asText(""));
            String rowAction = ev.path("action").asText(detail.path("action").asText(""));
            if (eventTypeFilter != null && !eventTypeFilter.isBlank()
                    && !workflowEvent.toLowerCase(Locale.ROOT).contains(eventTypeFilter.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (actionFilter != null && !actionFilter.isBlank()
                    && !rowAction.equalsIgnoreCase(actionFilter)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ev.path("id").asText(ev.path("eventId").asText("")));
            row.put("action", rowAction);
            row.put("event_type", workflowEvent);
            row.put("session_id", subjectRefSession(ev.path("subjectRef").asText("")));
            row.put("tenant_id", ev.path("tenantId").isMissingNode() ? null : ev.path("tenantId").asText());
            row.put("outcome", detail.path("outcome").asText(ev.path("outcome").asText("")));
            row.put("channel", detail.path("channel").isMissingNode() || detail.path("channel").isNull()
                    ? null : detail.path("channel").asText());
            row.put("template_key", detail.path("template_key").isMissingNode() || detail.path("template_key").isNull()
                    ? null : detail.path("template_key").asText());
            row.put("delivery_receipt_lag_ms",
                    detail.path("delivery_receipt_lag_ms").isMissingNode() || detail.path("delivery_receipt_lag_ms").isNull()
                            ? null : detail.path("delivery_receipt_lag_ms").asLong());
            row.put("notes", detail.path("notes").isMissingNode() || detail.path("notes").isNull()
                    ? null : detail.path("notes").asText());
            row.put("occurred_at", ev.path("createdAt").asText(ev.path("occurredAt").asText("")));
            JsonNode links = detail.path("links");
            row.put("links", links.isMissingNode() ? Map.of() : links);
            out.add(row);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private static String subjectRefSession(String subjectRef) {
        if (subjectRef == null || subjectRef.isBlank()) {
            return null;
        }
        int idx = subjectRef.indexOf(':');
        return idx >= 0 && idx < subjectRef.length() - 1 ? subjectRef.substring(idx + 1) : subjectRef;
    }
}
