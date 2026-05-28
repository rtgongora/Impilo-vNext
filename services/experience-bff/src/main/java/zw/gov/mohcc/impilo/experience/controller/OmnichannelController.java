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
import zw.gov.mohcc.impilo.experience.client.IntegrationHubServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;
import zw.gov.mohcc.impilo.experience.controller.dto.ApiEnvelope;
import zw.gov.mohcc.impilo.experience.controller.dto.ApiResponseFactory;
import zw.gov.mohcc.impilo.experience.controller.dto.comms.RoleDashboardDto;

import java.util.*;
import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Omnichannel orchestration controller.
 * Aggregates persisted data from support-service, campaigns-service, and integration-hub.
 */
@RestController
@RequestMapping("/internal/v1/omnichannel")
public class OmnichannelController {

    private static final Logger log = LoggerFactory.getLogger(OmnichannelController.class);
    private static final long SOURCE_COOLDOWN_SECONDS = 30L;
    private static final Map<String, OffsetDateTime> SOURCE_COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private final SupportServiceClient supportClient;
    private final CampaignsServiceClient campaignsClient;
    private final IntegrationHubServiceClient integrationHubClient;

    public OmnichannelController(
            SupportServiceClient supportClient,
            CampaignsServiceClient campaignsClient,
            IntegrationHubServiceClient integrationHubClient) {
        this.supportClient = supportClient;
        this.campaignsClient = campaignsClient;
        this.integrationHubClient = integrationHubClient;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiEnvelope<RoleDashboardDto>> dashboard(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        long startedNs = System.nanoTime();
        Map<String, Object> sourceHealth = new LinkedHashMap<>();

        int pendingCallbacks = 0;
        int escalatedCallbacks = 0;
        int activeCampaigns = 0;
        int completedCampaigns = 0;
        int activeSmsJourneys = 0;
        int activeChannels = 0;
        int ussdFlows = 0;
        int ivrFlows = 0;
        int disclosureRules = 0;

        if (isInCooldown("support")) {
            sourceHealth.put("support", "DEGRADED");
            Metrics.counter("impilo.omnichannel.dashboard.source_skipped", "source", "support").increment();
        } else try {
            long sourceStart = System.nanoTime();
            JsonNode tickets = supportClient.listTickets("OPEN");
            for (JsonNode item : asItemsArray(tickets)) {
                String status = toOmniStatus(text(item, "status"));
                if ("PENDING".equals(status)) {
                    pendingCallbacks++;
                }
                if ("ESCALATED".equalsIgnoreCase(text(item, "status"))) {
                    escalatedCallbacks++;
                }
            }
            sourceHealth.put("support", "UP");
            clearCooldown("support");
            Metrics.timer("impilo.omnichannel.dashboard.source_latency", "source", "support").record(System.nanoTime() - sourceStart, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            sourceHealth.put("support", "DOWN");
            markFailure("support");
            Metrics.counter("impilo.omnichannel.dashboard.source_errors", "source", "support").increment();
            log.warn("Omnichannel dashboard support source unavailable: {}", e.getMessage());
        }

        if (isInCooldown("campaigns")) {
            sourceHealth.put("campaigns", "DEGRADED");
            Metrics.counter("impilo.omnichannel.dashboard.source_skipped", "source", "campaigns").increment();
        } else try {
            long sourceStart = System.nanoTime();
            JsonNode campaigns = campaignsClient.listCampaigns(0, 100);
            for (JsonNode item : asItemsArray(campaigns)) {
                String status = text(item, "status");
                if ("COMPLETED".equalsIgnoreCase(status)) {
                    completedCampaigns++;
                } else {
                    activeCampaigns++;
                }
                if ("SMS".equalsIgnoreCase(text(item, "channel")) && !"COMPLETED".equalsIgnoreCase(status)) {
                    activeSmsJourneys++;
                }
            }
            sourceHealth.put("campaigns", "UP");
            clearCooldown("campaigns");
            Metrics.timer("impilo.omnichannel.dashboard.source_latency", "source", "campaigns").record(System.nanoTime() - sourceStart, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            sourceHealth.put("campaigns", "DOWN");
            markFailure("campaigns");
            Metrics.counter("impilo.omnichannel.dashboard.source_errors", "source", "campaigns").increment();
            log.warn("Omnichannel dashboard campaigns source unavailable: {}", e.getMessage());
        }

        if (isInCooldown("integration_routes")) {
            sourceHealth.put("integration_routes", "DEGRADED");
            Metrics.counter("impilo.omnichannel.dashboard.source_skipped", "source", "integration_routes").increment();
        } else try {
            long sourceStart = System.nanoTime();
            JsonNode routes = integrationHubClient.listRoutes();
            for (JsonNode item : asItemsArray(routes)) {
                boolean enabled = item.path("enabled").asBoolean(true);
                if (enabled) {
                    activeChannels++;
                }
                String targetService = item.path("targetService").asText("");
                String connectorType = item.path("connectorType").asText("");
                if (containsIgnoreCase(targetService, "ussd") || containsIgnoreCase(connectorType, "ussd")) {
                    ussdFlows++;
                }
                if (containsIgnoreCase(targetService, "ivr")
                        || containsIgnoreCase(targetService, "voice")
                        || containsIgnoreCase(connectorType, "ivr")) {
                    ivrFlows++;
                }
            }
            sourceHealth.put("integration_routes", "UP");
            clearCooldown("integration_routes");
            Metrics.timer("impilo.omnichannel.dashboard.source_latency", "source", "integration_routes").record(System.nanoTime() - sourceStart, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            sourceHealth.put("integration_routes", "DOWN");
            markFailure("integration_routes");
            Metrics.counter("impilo.omnichannel.dashboard.source_errors", "source", "integration_routes").increment();
            log.warn("Omnichannel dashboard routes source unavailable: {}", e.getMessage());
        }

        if (isInCooldown("mapping_templates")) {
            sourceHealth.put("mapping_templates", "DEGRADED");
            Metrics.counter("impilo.omnichannel.dashboard.source_skipped", "source", "mapping_templates").increment();
        } else try {
            long sourceStart = System.nanoTime();
            JsonNode templates = integrationHubClient.listMappingTemplates();
            for (JsonNode ignored : asItemsArray(templates)) {
                disclosureRules++;
            }
            sourceHealth.put("mapping_templates", "UP");
            clearCooldown("mapping_templates");
            Metrics.timer("impilo.omnichannel.dashboard.source_latency", "source", "mapping_templates").record(System.nanoTime() - sourceStart, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            sourceHealth.put("mapping_templates", "DOWN");
            markFailure("mapping_templates");
            Metrics.counter("impilo.omnichannel.dashboard.source_errors", "source", "mapping_templates").increment();
            log.warn("Omnichannel dashboard mapping source unavailable: {}", e.getMessage());
        }

        boolean partialData = sourceHealth.values().stream().anyMatch(v -> !"UP".equalsIgnoreCase(String.valueOf(v)));
        Map<String, Object> executive = Map.of(
                "omnichannel_reach_paths", activeChannels + ussdFlows + ivrFlows,
                "active_programs", activeCampaigns + activeSmsJourneys
        );
        Map<String, Object> operations = Map.of(
                "pending_callbacks", pendingCallbacks,
                "escalated_callbacks", escalatedCallbacks,
                "active_sms_journeys", activeSmsJourneys,
                "configured_channels", activeChannels
        );
        Map<String, Object> clinical = Map.of(
                "callback_clinical_followups", pendingCallbacks,
                "high_risk_escalations", escalatedCallbacks
        );
        Map<String, Object> communications = Map.of(
                "active_campaigns", activeCampaigns,
                "completed_campaigns", completedCampaigns,
                "ussd_flows", ussdFlows,
                "ivr_flows", ivrFlows,
                "disclosure_rules", disclosureRules
        );
        Map<String, Object> governance = Map.of(
                "source_health", sourceHealth,
                "partial_data", partialData,
                "trend_window", "7d",
                "last_refreshed_at", java.time.OffsetDateTime.now().toString()
        );
        RoleDashboardDto dashboard = new RoleDashboardDto(executive, operations, clinical, communications, governance);
        Metrics.timer("impilo.omnichannel.dashboard.latency").record(System.nanoTime() - startedNs, TimeUnit.NANOSECONDS);
        return ResponseEntity.ok(ApiResponseFactory.ok(dashboard, requestId, null));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> health(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        Map<String, String> checks = new LinkedHashMap<>();
        try {
            supportClient.listTickets("OPEN");
            checks.put("support", "UP");
        } catch (Exception e) {
            checks.put("support", "DOWN");
        }
        try {
            campaignsClient.listCampaigns(0, 1);
            checks.put("campaigns", "UP");
        } catch (Exception e) {
            checks.put("campaigns", "DOWN");
        }
        boolean healthy = checks.values().stream().allMatch("UP"::equals);
        return ResponseEntity.ok(ApiResponseFactory.ok(
                Map.of("healthy", healthy, "checks", checks, "checked_at", OffsetDateTime.now().toString()),
                requestId,
                null
        ));
    }

    // ── Callbacks ────────────────────────────────────────────────

    @GetMapping("/callbacks")
    public ResponseEntity<Map<String, Object>> listCallbacks(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(required = false) String status) {

        try {
            JsonNode tickets = supportClient.listTickets(toSupportStatus(status));
            List<Map<String, Object>> callbacks = new ArrayList<>();
            for (JsonNode item : asItemsArray(tickets)) {
                callbacks.add(toCallback(item));
            }
            return ResponseEntity.ok(Map.of("data", callbacks, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.warn("Omnichannel callbacks unavailable: {}", e.getMessage());
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    @PostMapping("/callbacks")
    public ResponseEntity<Map<String, Object>> createCallback(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {

        try {
            Map<String, Object> ticketRequest = new LinkedHashMap<>();
            String callerId = body.getOrDefault("callerId", body.getOrDefault("caller_id", "unknown"));
            String callerName = body.getOrDefault("callerName", body.getOrDefault("caller_name", callerId));
            String reason = body.getOrDefault("reason", "Callback requested via omnichannel queue");
            ticketRequest.put("title", "Callback: " + callerName);
            ticketRequest.put("description", reason);
            ticketRequest.put("reporterRef", callerId);
            ticketRequest.put("category", "OMNICHANNEL_CALLBACK");
            ticketRequest.put("priority", toSupportPriority(body.get("priority")));
            JsonNode ticket = supportClient.createTicket(ticketRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of("data", toCallback(ticket), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    @PostMapping("/callbacks/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeCallback(
            @PathVariable UUID id, @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody(required = false) Map<String, String> body) {

        try {
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("status", "RESOLVED");
            update.put("resolution", body != null ? body.getOrDefault("notes", "Completed from omnichannel queue") : "Completed from omnichannel queue");
            JsonNode ticket = supportClient.updateTicket(id, update);
            return ResponseEntity.ok(Map.of("data", toCallback(ticket), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    // ── Channel Configs ──────────────────────────────────────────

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> listChannels(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode routes = integrationHubClient.listRoutes();
            List<Map<String, Object>> channels = new ArrayList<>();
            for (JsonNode route : asItemsArray(routes)) {
                channels.add(toChannelConfig(route));
            }
            return ResponseEntity.ok(Map.of("data", channels, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    // ── Campaigns (admin) ────────────────────────────────────────

    @GetMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> listCampaigns(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            JsonNode campaigns = campaignsClient.listCampaigns(page, size);
            return ResponseEntity.ok(Map.of("data", campaigns != null ? campaigns : List.of(), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    @PostMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode campaign = campaignsClient.createCampaign(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", campaign != null ? campaign : Map.of(),
                    "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    // ── SMS Journeys ─────────────────────────────────────────────

    @GetMapping("/sms-journeys")
    public ResponseEntity<Map<String, Object>> listSmsJourneys(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode campaigns = campaignsClient.listCampaigns(0, 100);
            List<Map<String, Object>> journeys = new ArrayList<>();
            for (JsonNode campaign : asItemsArray(campaigns)) {
                if ("SMS".equalsIgnoreCase(campaign.path("channel").asText())) {
                    journeys.add(toSmsJourney(campaign));
                }
            }
            return ResponseEntity.ok(Map.of("data", journeys, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    @PostMapping("/sms-journeys")
    public ResponseEntity<Map<String, Object>> createSmsJourney(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {
        try {
            Map<String, Object> create = new LinkedHashMap<>();
            create.put("name", body.getOrDefault("name", "SMS Journey"));
            create.put("description", "Generated from omnichannel composer");
            create.put("campaignType", "OMNICHANNEL_SMS_JOURNEY");
            create.put("targetGroup", "{\"channel\":\"SMS\"}");
            create.put("messageTemplate", body.getOrDefault("messageTemplate", ""));
            create.put("channel", "SMS");
            JsonNode campaign = campaignsClient.createCampaign(create);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", toSmsJourney(campaign),
                    "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    // ── USSD Menus ───────────────────────────────────────────────

    @GetMapping("/ussd-menus")
    public ResponseEntity<Map<String, Object>> listUssdMenus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode routes = integrationHubClient.listRoutes();
            List<Map<String, Object>> menus = new ArrayList<>();
            for (JsonNode route : asItemsArray(routes)) {
                if (containsIgnoreCase(route.path("targetService").asText(), "ussd")
                        || containsIgnoreCase(route.path("connectorType").asText(), "ussd")) {
                    menus.add(toUssdMenu(route));
                }
            }
            return ResponseEntity.ok(Map.of("data", menus, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    // ── IVR Flows ────────────────────────────────────────────────

    @GetMapping("/ivr-flows")
    public ResponseEntity<Map<String, Object>> listIvrFlows(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode routes = integrationHubClient.listRoutes();
            List<Map<String, Object>> flows = new ArrayList<>();
            for (JsonNode route : asItemsArray(routes)) {
                if (containsIgnoreCase(route.path("targetService").asText(), "ivr")
                        || containsIgnoreCase(route.path("targetService").asText(), "voice")
                        || containsIgnoreCase(route.path("connectorType").asText(), "ivr")) {
                    flows.add(toIvrFlow(route));
                }
            }
            return ResponseEntity.ok(Map.of("data", flows, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    // ── Disclosure Rules ─────────────────────────────────────────

    @GetMapping("/disclosure-rules")
    public ResponseEntity<Map<String, Object>> listDisclosureRules(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode templates = integrationHubClient.listMappingTemplates();
            List<Map<String, Object>> rules = new ArrayList<>();
            for (JsonNode item : asItemsArray(templates)) {
                rules.add(toDisclosureRule(item));
            }
            return ResponseEntity.ok(Map.of("data", rules, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    @PostMapping("/disclosure-rules")
    public ResponseEntity<Map<String, Object>> createDisclosureRule(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, String> body) {
        try {
            Map<String, Object> create = new LinkedHashMap<>();
            String channel = body.getOrDefault("channelType", "SMS").toUpperCase(Locale.ROOT);
            String category = body.getOrDefault("dataCategory", "DEMOGRAPHICS").toUpperCase(Locale.ROOT);
            String level = body.getOrDefault("disclosureLevel", "MINIMAL").toUpperCase(Locale.ROOT);
            create.put("name", "Disclosure " + channel + " " + category + " " + level);
            create.put("sourceFormat", channel);
            create.put("targetFormat", "DISCLOSURE_POLICY");
            create.put("mappingRulesJson", "{\"dataCategory\":\"" + category + "\",\"disclosureLevel\":\"" + level + "\"}");
            create.put("active", true);
            JsonNode result = integrationHubClient.createMappingTemplate(create);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", toDisclosureRule(result), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return upstreamFailure("OMNICHANNEL_UNAVAILABLE", e.getMessage(), requestId);
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(String code, String message, String requestId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Omnichannel upstream unavailable"),
                "meta", Map.of("request_id", requestId)));
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

    private Map<String, Object> toCallback(JsonNode ticket) {
        Map<String, Object> out = new LinkedHashMap<>();
        String id = text(ticket, "ticketId", "id");
        String reporter = text(ticket, "reporterRef", "callerId", "caller_id");
        String title = text(ticket, "title", "callerName", "caller_name");
        out.put("id", id);
        out.put("channel", "PHONE");
        out.put("caller_id", reporter);
        out.put("caller_name", title != null && title.startsWith("Callback:") ? title.replace("Callback:", "").trim() : title);
        out.put("reason", text(ticket, "description", "reason"));
        out.put("priority", toOmniPriority(text(ticket, "priority")));
        out.put("status", toOmniStatus(text(ticket, "status")));
        out.put("assigned_to", text(ticket, "assigneeRef", "assigned_to"));
        out.put("scheduled_at", text(ticket, "scheduledAt", "scheduled_at"));
        out.put("completed_at", text(ticket, "resolvedAt", "completed_at"));
        out.put("notes", text(ticket, "resolution", "notes"));
        out.put("created_at", text(ticket, "createdAt", "created_at"));
        return out;
    }

    private Map<String, Object> toChannelConfig(JsonNode route) {
        Map<String, Object> out = new LinkedHashMap<>();
        String targetService = route.path("targetService").asText("");
        String connectorType = route.path("connectorType").asText("");
        String channelType = !connectorType.isBlank() ? connectorType.toUpperCase(Locale.ROOT) : inferChannelType(targetService);
        out.put("id", text(route, "id"));
        out.put("channel_type", channelType);
        out.put("name", targetService.isBlank() ? text(route, "sourceService", "id") : targetService);
        out.put("config", Map.of(
                "targetUrl", text(route, "targetUrl"),
                "eventTypePrefix", text(route, "eventTypePrefix"),
                "matchMethod", text(route, "matchMethod"),
                "matchPathRegex", text(route, "matchPathRegex")
        ));
        out.put("is_active", route.path("enabled").asBoolean(true));
        out.put("created_at", text(route, "createdAt"));
        out.put("updated_at", text(route, "updatedAt"));
        return out;
    }

    private Map<String, Object> toSmsJourney(JsonNode campaign) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", text(campaign, "id"));
        out.put("name", text(campaign, "name"));
        out.put("trigger_event", text(campaign, "campaignType"));
        out.put("message_template", text(campaign, "messageTemplate"));
        out.put("schedule_cron", null);
        out.put("is_active", !"COMPLETED".equalsIgnoreCase(text(campaign, "status")));
        out.put("sent_count", null);
        out.put("created_at", text(campaign, "createdAt"));
        return out;
    }

    private Map<String, Object> toUssdMenu(JsonNode route) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", text(route, "id"));
        out.put("short_code", text(route, "matchPathRegex", "targetService"));
        out.put("menu_tree", Map.of(
                "targetUrl", text(route, "targetUrl"),
                "eventTypePrefix", text(route, "eventTypePrefix")
        ));
        out.put("is_active", route.path("enabled").asBoolean(true));
        out.put("created_at", text(route, "createdAt"));
        return out;
    }

    private Map<String, Object> toIvrFlow(JsonNode route) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", text(route, "id"));
        out.put("name", text(route, "targetService", "id"));
        out.put("phone_number", text(route, "matchPathRegex"));
        out.put("flow_definition", Map.of(
                "targetUrl", text(route, "targetUrl"),
                "eventTypePrefix", text(route, "eventTypePrefix")
        ));
        out.put("is_active", route.path("enabled").asBoolean(true));
        out.put("created_at", text(route, "createdAt"));
        return out;
    }

    private Map<String, Object> toDisclosureRule(JsonNode item) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", text(item, "id"));
        out.put("channel_type", text(item, "sourceFormat", "channelType", "channel_type"));
        out.put("data_category", extractRuleField(item.path("mappingRulesJson").asText(""), "dataCategory", "DEMOGRAPHICS"));
        out.put("disclosure_level", extractRuleField(item.path("mappingRulesJson").asText(""), "disclosureLevel", "MINIMAL"));
        out.put("is_active", item.path("active").asBoolean(true));
        out.put("created_at", text(item, "createdAt"));
        return out;
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

    private String toSupportStatus(String status) {
        if (status == null || status.isBlank()) {
            return "OPEN";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PENDING", "ASSIGNED", "IN_PROGRESS" -> "OPEN";
            case "COMPLETED" -> "RESOLVED";
            case "ESCALATED" -> "ESCALATED";
            case "CANCELLED" -> "CANCELLED";
            default -> status.toUpperCase(Locale.ROOT);
        };
    }

    private String toSupportPriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "NORMAL";
        }
        return priority.toUpperCase(Locale.ROOT);
    }

    private String toOmniPriority(String supportPriority) {
        if (supportPriority == null || supportPriority.isBlank()) {
            return "NORMAL";
        }
        return supportPriority.toUpperCase(Locale.ROOT);
    }

    private String toOmniStatus(String supportStatus) {
        if (supportStatus == null || supportStatus.isBlank()) {
            return "PENDING";
        }
        return switch (supportStatus.toUpperCase(Locale.ROOT)) {
            case "RESOLVED", "CLOSED" -> "COMPLETED";
            case "OPEN", "ASSIGNED" -> "PENDING";
            default -> supportStatus.toUpperCase(Locale.ROOT);
        };
    }

    private String inferChannelType(String targetService) {
        String ts = targetService == null ? "" : targetService.toLowerCase(Locale.ROOT);
        if (ts.contains("ussd")) return "USSD";
        if (ts.contains("ivr") || ts.contains("voice")) return "IVR";
        if (ts.contains("sms")) return "SMS";
        if (ts.contains("whatsapp")) return "WHATSAPP";
        if (ts.contains("email")) return "EMAIL";
        return "API";
    }

    private boolean containsIgnoreCase(String value, String token) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    private String extractRuleField(String rawJson, String field, String defaultValue) {
        if (rawJson == null || rawJson.isBlank()) {
            return defaultValue;
        }
        String marker = "\"" + field + "\":\"";
        int idx = rawJson.indexOf(marker);
        if (idx < 0) {
            return defaultValue;
        }
        int start = idx + marker.length();
        int end = rawJson.indexOf("\"", start);
        if (end <= start) {
            return defaultValue;
        }
        return rawJson.substring(start, end);
    }

    private boolean isInCooldown(String source) {
        OffsetDateTime until = SOURCE_COOLDOWN_UNTIL.get(source);
        return until != null && until.isAfter(OffsetDateTime.now());
    }

    private void markFailure(String source) {
        SOURCE_COOLDOWN_UNTIL.put(source, OffsetDateTime.now().plusSeconds(SOURCE_COOLDOWN_SECONDS));
    }

    private void clearCooldown(String source) {
        SOURCE_COOLDOWN_UNTIL.remove(source);
    }
}
