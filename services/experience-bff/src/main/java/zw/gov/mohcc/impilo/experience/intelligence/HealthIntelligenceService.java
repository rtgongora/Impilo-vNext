package zw.gov.mohcc.impilo.experience.intelligence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.DagsServiceClient;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SearchServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-service Health Intelligence orchestration.
 * <p>
 * Distinguishes retrieval, summarisation, recommendation, and decision-support outputs
 * without performing governed mutations. Downstream failures are isolated per source.
 * </p>
 */
@Service
public class HealthIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(HealthIntelligenceService.class);

    private static final Set<String> ROW_LEVEL_ENTITY_TYPES = Set.of(
            "PATIENT", "CLIENT", "PERSON", "CITIZEN", "ENCOUNTER", "OBSERVATION", "DIAGNOSTIC_REPORT");

    private final GuidanceServiceClient guidance;
    private final SearchServiceClient search;
    private final PctServiceClient pct;
    private final VitoServiceClient vito;
    private final VarapiServiceClient varapi;
    private final LearningServiceClient learning;
    private final DagsServiceClient dags;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<IntelligenceEventPublisher> eventPublisher;

    public HealthIntelligenceService(
            GuidanceServiceClient guidance,
            SearchServiceClient search,
            PctServiceClient pct,
            VitoServiceClient vito,
            VarapiServiceClient varapi,
            LearningServiceClient learning,
            DagsServiceClient dags,
            ObjectMapper objectMapper,
            ObjectProvider<IntelligenceEventPublisher> eventPublisher) {
        this.guidance = guidance;
        this.search = search;
        this.pct = pct;
        this.vito = vito;
        this.varapi = varapi;
        this.learning = learning;
        this.dags = dags;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public Map<String, Object> executeQuery(
            Map<String, Object> body,
            String tenantId,
            String requestId,
            String correlationId,
            @Nullable String actorId) {
        String queryType = String.valueOf(body.getOrDefault("queryType", "SEARCH_FUSED"));
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.getOrDefault("context", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) body.getOrDefault("input", Map.of());

        String queryId = UUID.randomUUID().toString();
        ObjectNode result = objectMapper.createObjectNode();
        result.put("queryId", queryId);
        result.put("queryType", queryType);
        result.put("status", "COMPLETED");
        result.put("generatedAt", OffsetDateTime.now().toString());

        try {
            switch (queryType) {
                case "SEARCH_FUSED" -> fuseSearch(result, input, context);
                case "SUMMARY_QUEUE" -> summarizeQueues(result, context);
                case "SUMMARY_PROVIDER" -> summarizeProvider(result, context);
                case "SUMMARY_CLIENT" -> summarizeClient(result, context);
                case "HELP_DESK_ASSIST" -> helpdeskAssist(result, input, context);
                case "DATA_QUALITY_HINTS" -> dataQualityHints(result, context, input);
                case "ANOMALY_SCAN_OPERATIONS" -> anomalyScanOperations(result, context);
                case "WORKFLOW_ASSIST" -> workflowAssist(result, input, context);
                case "LEARNING_NUDGE" -> learningNudge(result, context, input);
                default -> fuseSearch(result, input, context);
            }
        } catch (Exception ex) {
            log.warn("Intelligence query {} failed: {}", queryType, ex.getMessage());
            result.put("status", "PARTIAL");
            result.put("error", ex.getClass().getSimpleName());
            result.put("message", ex.getMessage());
        }

        emit("intelligence.query.executed", Map.of(
                "queryId", queryId,
                "queryType", queryType,
                "tenantId", tenantId,
                "requestId", requestId,
                "correlationId", correlationId,
                "actorId", actorId == null ? "" : actorId,
                "status", result.path("status").asText()));

        if (result.has("summary")) {
            emit("intelligence.summary.generated", Map.of(
                    "queryId", queryId,
                    "correlationId", correlationId,
                    "subjectType", context.getOrDefault("subjectType", "")));
        }
        if (result.has("recommendations")) {
            emit("intelligence.recommendation.generated", Map.of("queryId", queryId, "correlationId", correlationId));
        }
        if (result.has("anomalySignals")) {
            emit("intelligence.anomaly.detected", Map.of("queryId", queryId, "correlationId", correlationId));
        }
        if ("HELP_DESK_ASSIST".equals(queryType)) {
            emit("intelligence.helpdesk.assist.used", Map.of("queryId", queryId, "correlationId", correlationId));
        }
        if ("LEARNING_NUDGE".equals(queryType)) {
            emit("intelligence.learning.suggestion.generated", Map.of("queryId", queryId, "correlationId", correlationId));
        }
        if ("WORKFLOW_ASSIST".equals(queryType)) {
            emit("intelligence.workflow.assist.generated", Map.of("queryId", queryId, "correlationId", correlationId));
        }

        applyVisibilityEnvelope(context, result);

        structuredAuditLog(tenantId, requestId, correlationId, actorId, queryType, queryId, result.path("status").asText());

        return Map.of("data", objectMapper.convertValue(result, new TypeReference<Map<String, Object>>() {}));
    }

    /** Lightweight fusion for shell search (GET). */
    public Map<String, Object> shellSuggest(
            String q,
            @Nullable String facilityId,
            String correlationId,
            String tenantId,
            @Nullable Map<String, Object> visibilityContext,
            @Nullable String rankMode) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("queryId", UUID.randomUUID().toString());
        root.put("correlationId", correlationId);
        Map<String, Object> vis = visibilityContext == null ? Map.of() : visibilityContext;
        boolean aggregateOnly = isAggregateOnly(vis);
        ArrayNode hints = root.putArray("hints");
        try {
            String rm = rankMode == null || rankMode.isBlank() ? "lexical" : rankMode;
            JsonNode idx = search.search(q, null, 0, 6, rm);
            addSearchHits(hints, idx, "platform_index", aggregateOnly);
        } catch (Exception e) {
            log.debug("shellSuggest search index skipped: {}", e.getMessage());
        }
        try {
            JsonNode g = guidance.search(q, "all", 0, 4);
            addGuidanceHits(hints, g, "guidance_knowledge");
        } catch (Exception e) {
            log.debug("shellSuggest guidance skipped: {}", e.getMessage());
        }
        emit("intelligence.query.executed", Map.of(
                "queryId", root.path("queryId").asText(),
                "queryType", "SHELL_SUGGEST",
                "correlationId", correlationId,
                "facilityId", facilityId == null ? "" : facilityId));
        structuredAuditLog(tenantId, "", correlationId, null, "SHELL_SUGGEST", root.path("queryId").asText(), "COMPLETED");
        return Map.of("data", objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {}));
    }

    public List<Map<String, Object>> buildAssistantNotifications(
            @Nullable String workMode, String tenantId, @Nullable String facilityId, @Nullable String shiftId) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (workMode == null || workMode.isBlank()) {
            return out;
        }
        out.add(notification(
                "INSIGHT",
                "INFO",
                "Health Intelligence",
                "Cross-service intelligence is active. Use Ask, Search, or the Intelligence hub for grounded summaries.",
                true,
                Map.of("href", "/intelligence", "label", "Open Intelligence hub")));

        if ("clinical".equalsIgnoreCase(workMode) && facilityId != null && !facilityId.isBlank()) {
            try {
                JsonNode queues = pct.listQueues(java.util.UUID.fromString(facilityId), null);
                int n = queues != null && queues.isArray() ? queues.size() : 0;
                if (n > 0) {
                    out.add(notification(
                            "OPERATIONAL",
                            n > 5 ? "MEDIUM" : "LOW",
                            "Queue intelligence",
                            "Facility has " + n + " active queue definition(s). Review workload before peak hours.",
                            true,
                            Map.of("href", "/queue", "label", "Open queue")));
                }
            } catch (Exception e) {
                log.debug("Assistant queue intelligence skipped: {}", e.getMessage());
            }
        }
        if ("wellness".equalsIgnoreCase(workMode)) {
            out.add(notification(
                    "WELLNESS",
                    "LOW",
                    "Learning-aware wellness",
                    "Guidance education and reminders stay consent-governed. Visit Guidance → Education for Fundo-aligned tips.",
                    true,
                    Map.of("href", "/guidance/education", "label", "Health education")));
        }
        return out;
    }

    private static Map<String, Object> notification(
            String type,
            String severity,
            String title,
            String body,
            boolean dismissible,
            Map<String, String> action) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", UUID.randomUUID().toString());
        m.put("type", type);
        m.put("severity", severity);
        m.put("title", title);
        m.put("body", body);
        m.put("dismissible", dismissible);
        m.put("timestamp", OffsetDateTime.now().toString());
        m.put("source", "health-intelligence-plane");
        if (action != null && action.containsKey("href")) {
            m.put("action", Map.of("label", action.getOrDefault("label", "Open"), "href", action.get("href")));
        }
        return m;
    }

    private void fuseSearch(ObjectNode result, Map<String, Object> input, Map<String, Object> context) {
        String q = String.valueOf(input.getOrDefault("text", ""));
        ArrayNode sources = result.putArray("sourceRefs");
        ArrayNode ranked = result.putArray("rankedHits");
        if (q.isBlank()) {
            result.put("summary", "Provide a non-empty query for fused search.");
            return;
        }
        boolean aggregateOnly = isAggregateOnly(context);
        String rankMode = stringAttr(input, "rankMode", stringAttr(context, "rankMode", "recency"));
        String rankParam = "recency".equalsIgnoreCase(rankMode) ? null : rankMode;
        try {
            JsonNode idx = search.search(q, stringAttr(context, "entityType"), 0, 12, rankParam);
            addSearchHits(ranked, idx, "platform_index", aggregateOnly);
            sources.addObject().put("system", "search-service").put("role", "retrieval");
        } catch (Exception e) {
            sources.addObject().put("system", "search-service").put("error", e.getMessage());
        }
        try {
            JsonNode g = guidance.search(q, stringAttr(context, "domain", "all"), 0, 8);
            addGuidanceHits(ranked, g, "guidance_knowledge");
            sources.addObject().put("system", "guidance-service").put("role", "retrieval");
        } catch (Exception e) {
            sources.addObject().put("system", "guidance-service").put("error", e.getMessage());
        }
        result.put("resultType", "RETRIEVAL");
        result.put("summary", "Fused platform index + guidance knowledge for supervised discovery.");
        result.putObject("structuredOutput")
                .put("retrieval", "FUSED")
                .put("visibilityTier", stringAttr(context, "visibilityTier", "standard"))
                .put("rankMode", rankMode)
                .put("aggregateRowHitsSuppressed", aggregateOnly);
    }

    private void summarizeQueues(ObjectNode result, Map<String, Object> context) {
        String facilityId = stringAttr(context, "facilityId");
        if (facilityId.isBlank()) {
            result.put("summary", "facilityId is required in context for queue summarisation.");
            result.put("resultType", "SUMMARY");
            return;
        }
        JsonNode queues = pct.listQueues(java.util.UUID.fromString(facilityId), null);
        result.put("resultType", "SUMMARY");
        result.put("summary", "Queue definitions for facility " + facilityId + ": " + describeArraySize(queues));
        result.set("structuredOutput", queues == null ? objectMapper.createObjectNode() : queues);
        result.putArray("sourceRefs").addObject().put("system", "pct").put("path", "/v1/queues");
    }

    private void summarizeProvider(ObjectNode result, Map<String, Object> context) {
        String pid = stringAttr(context, "subjectId");
        if (pid.isBlank()) {
            result.put("summary", "subjectId (provider) required in context.");
            result.put("resultType", "SUMMARY");
            return;
        }
        result.put("resultType", "SUMMARY");
        if (isAggregateOnly(context)) {
            result.put("summary", "Aggregate-only visibility: provider registry detail withheld; use council workflows for governed access.");
            result.set("structuredOutput", aggregateOnlyPlaceholder("PROVIDER_REGISTRY"));
            result.putArray("sourceRefs").addObject().put("system", "varapi").put("role", "withheld");
            return;
        }
        ObjectNode structured = objectMapper.createObjectNode();
        try {
            structured.set("provider", varapi.getProvider(pid));
        } catch (Exception e) {
            structured.put("provider_error", e.getMessage());
        }
        try {
            structured.set("cpdSummary", varapi.getProviderCpdSummary(pid));
        } catch (Exception e) {
            structured.put("cpd_error", e.getMessage());
        }
        try {
            long numericId = Long.parseLong(pid.trim());
            structured.set("fundoCpdCandidates", varapi.getFundoCpdCandidates(numericId));
        } catch (Exception e) {
            structured.put("fundo_error", "Fundo CPD candidates require numeric provider id: " + e.getMessage());
        }
        result.put("summary", "Provider lifecycle snapshot (Varapi) with Fundo-linked CPD candidates where available.");
        result.set("structuredOutput", structured);
        result.putArray("sourceRefs").addObject().put("system", "varapi").put("role", "retrieval");
    }

    private void summarizeClient(ObjectNode result, Map<String, Object> context) {
        String healthId = stringAttr(context, "subjectId");
        if (healthId.isBlank()) {
            result.put("summary", "subjectId (healthId) required in context.");
            result.put("resultType", "SUMMARY");
            return;
        }
        result.put("resultType", "SUMMARY");
        if (isAggregateOnly(context)) {
            result.put("summary", "Aggregate-only visibility: client identity drill-down withheld in intelligence plane.");
            result.set("structuredOutput", aggregateOnlyPlaceholder("CLIENT_IDENTITY"));
            result.putArray("sourceRefs").addObject().put("system", "vito").put("role", "withheld");
            return;
        }
        try {
            JsonNode resolved = vito.resolveIdentity(healthId);
            result.put("summary", "Identity resolution summary for governed client context.");
            result.set("structuredOutput", resolved);
            result.putArray("sourceRefs").addObject().put("system", "vito").put("role", "retrieval");
        } catch (Exception e) {
            result.put("status", "PARTIAL");
            result.put("summary", "Could not resolve client in intelligence layer: " + e.getMessage());
        }
    }

    private void helpdeskAssist(ObjectNode result, Map<String, Object> input, Map<String, Object> context) {
        String issue = String.valueOf(input.getOrDefault("text", ""));
        result.put("resultType", "DECISION_SUPPORT");
        if (issue.isBlank()) {
            result.put("summary", "Describe the issue in input.text for helpdesk assistance.");
            return;
        }
        try {
            JsonNode hits = guidance.search(issue, "all", 0, 6);
            result.set("knowledgeHits", hits);
        } catch (Exception e) {
            result.putObject("knowledgeHits").put("error", e.getMessage());
        }
        try {
            JsonNode edu = guidance.getEducation("all", 0, 4);
            result.set("learningSuggestions", edu);
        } catch (Exception e) {
            result.putObject("learningSuggestions").put("error", e.getMessage());
        }
        if (learning.isConfigured()) {
            String issueType = stringAttr(input, "issueType", "GENERAL");
            String subjectType = stringAttr(context, "subjectType");
            String subjectId = stringAttr(context, "subjectId");
            try {
                JsonNode svcHd = learning.getHelpdeskLearning(
                        issueType,
                        subjectType.isBlank() ? null : subjectType,
                        subjectId.isBlank() ? null : subjectId);
                if (svcHd != null) {
                    result.set("learningServiceHelpdesk", svcHd);
                }
            } catch (Exception e) {
                result.putObject("learningServiceHelpdesk").put("error", e.getMessage());
            }
            try {
                JsonNode svcHits = learning.searchHits(issue, 8);
                if (svcHits != null) {
                    result.set("learningServiceSearchHits", svcHits);
                }
            } catch (Exception e) {
                result.putObject("learningServiceSearchHits").put("error", e.getMessage());
            }
        }
        maybeAttachDagsGovernanceSummary(input, result);

        ArrayNode recs = result.putArray("recommendations");
        recs.addObject()
                .put("recommendationType", "ROUTE_TO_KB")
                .put("summary", "Review matched knowledge articles before escalating.")
                .put("rationale", "Issue text matched against guidance federated index.")
                .put("priority", "MEDIUM")
                .put("state", "SUGGESTED");
        recs.addObject()
                .put("recommendationType", "ROUTE_TO_LEARNING")
                .put("summary", "Open Health Education for Fundo-aligned micro-learning.")
                .put("rationale", "Teach-not-only-tell: link user to /guidance/education.")
                .put("priority", "LOW")
                .put("state", "SUGGESTED");
        result.put("summary", "Helpdesk triage pack: knowledge hits + learning routes (human must confirm actions).");
        ArrayNode refs = result.putArray("sourceRefs");
        refs.addObject().put("system", "guidance-service").put("role", "retrieval");
        if (learning.isConfigured()) {
            refs.addObject().put("system", "learning-service").put("role", "retrieval");
        }
    }

    private void dataQualityHints(ObjectNode result, Map<String, Object> context, Map<String, Object> input) {
        result.put("resultType", "DATA_QUALITY");
        ArrayNode hints = result.putArray("structuredOutput");
        String subjectType = stringAttr(context, "subjectType");
        if ("CLIENT".equalsIgnoreCase(subjectType)) {
            hints.addObject()
                    .put("hintType", "DUPLICATE_RISK")
                    .put("summary", "Run search-before-create in registry intake before issuing a new Health ID.")
                    .put("severity", "INFO");
        }
        if ("PROVIDER".equalsIgnoreCase(subjectType)) {
            hints.addObject()
                    .put("hintType", "COUNCIL_ALIGNMENT")
                    .put("summary", "Verify council obligations and Fundo CPD linkage in provider council self-service.")
                    .put("severity", "INFO");
        }
        String freeText = String.valueOf(input.getOrDefault("text", ""));
        if (!freeText.isBlank()) {
            hints.addObject()
                    .put("hintType", "TEXT_TRIAGE")
                    .put("summary", "Structured registry review recommended for narrative: " + freeText.substring(0, Math.min(120, freeText.length())))
                    .put("severity", "LOW");
        }
        result.put("summary", "Heuristic data-quality hints — not automated merges.");
        result.putArray("sourceRefs").addObject().put("system", "health-intelligence-plane").put("role", "heuristic");
    }

    private void anomalyScanOperations(ObjectNode result, Map<String, Object> context) {
        result.put("resultType", "ANOMALY");
        ArrayNode signals = result.putArray("anomalySignals");
        String facilityId = stringAttr(context, "facilityId");
        if (facilityId.isBlank()) {
            result.put("summary", "facilityId required for operational anomaly scan.");
            return;
        }
        try {
            JsonNode queues = pct.listQueues(java.util.UUID.fromString(facilityId), null);
            int n = queues != null && queues.isArray() ? queues.size() : 0;
            if (n > 8) {
                signals.addObject()
                        .put("signalType", "QUEUE_FAN_OUT")
                        .put("severity", "MEDIUM")
                        .put("summary", "High number of queue definitions may indicate configuration drift.")
                        .put("evidenceSummary", "queueCount=" + n);
            }
        } catch (Exception e) {
            signals.addObject()
                    .put("signalType", "DATA_UNAVAILABLE")
                    .put("severity", "INFO")
                    .put("summary", "Could not evaluate queues: " + e.getMessage());
        }
        result.put("summary", "Operational anomaly scan (foundation only).");
    }

    private void workflowAssist(ObjectNode result, Map<String, Object> input, Map<String, Object> context) {
        result.put("resultType", "WORKFLOW_ASSIST");
        ObjectNode assist = result.putObject("structuredOutput");
        assist.put("workflowCode", stringAttr(context, "workflowCode", "UNKNOWN"));
        assist.putArray("suggestedSteps")
                .add("Capture structured facts in the workflow form.")
                .add("Validate identifiers against registry search.")
                .add("Escalate if policy blocks automation.");
        assist.putObject("suggestedFields")
                .put("notes", "Draft review notes here — human approval required before commit.");
        result.putArray("recommendations")
                .addObject()
                .put("recommendationType", "PREFILL_REVIEW")
                .put("summary", "Use Intelligence summaries as read-only briefing inputs.")
                .put("rationale", "Automation must not silently finalize sensitive registry or clinical actions.")
                .put("priority", "LOW")
                .put("state", "SUGGESTED");
        result.put("summary", "Workflow copilot hints (non-automating).");
    }

    private void learningNudge(ObjectNode result, Map<String, Object> context, Map<String, Object> input) {
        result.put("resultType", "LEARNING");
        String domain = stringAttr(context, "domain", "all");
        try {
            JsonNode edu = guidance.getEducation(domain, 0, 6);
            result.set("learningAwareSuggestions", edu);
        } catch (Exception e) {
            result.putObject("learningAwareSuggestions").put("error", e.getMessage());
        }
        result.put("summary", "Learning-aware suggestions via guidance education index (Fundo linkage in provider flows).");
        result.putArray("sourceRefs").addObject().put("system", "guidance-service").put("path", "/internal/v1/guidance/education");
    }

    private void addSearchHits(ArrayNode ranked, JsonNode idxRoot, String sourceTag, boolean aggregateOnly) {
        if (idxRoot == null) {
            return;
        }
        JsonNode data = idxRoot.has("data") ? idxRoot.get("data") : idxRoot;
        JsonNode results = data.get("results");
        if (results == null || !results.isArray()) {
            return;
        }
        for (JsonNode hit : results) {
            if (aggregateOnly && isSensitiveIndexHit(hit)) {
                continue;
            }
            ObjectNode o = ranked.addObject();
            o.put("source", sourceTag);
            o.set("hit", hit);
        }
    }

    private static boolean isSensitiveIndexHit(JsonNode hit) {
        if (hit == null || !hit.isObject()) {
            return false;
        }
        String et = hit.path("entityType").asText("");
        return ROW_LEVEL_ENTITY_TYPES.contains(et.toUpperCase(Locale.ROOT));
    }

    private void applyVisibilityEnvelope(Map<String, Object> context, ObjectNode result) {
        Object ev = context.get("effectiveVisibility");
        if (ev != null) {
            result.set("effectiveVisibility", objectMapper.valueToTree(ev));
            result.put("policyApplied", "VISIBILITY_HEADERS+JWT");
        } else {
            result.put("policyApplied", "JWT_EDGE_ONLY");
        }
    }

    private static boolean isAggregateOnly(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        Object ev = context.get("effectiveVisibility");
        if (ev instanceof Map<?, ?> m) {
            Object v = m.get("aggregateOnly");
            if (v instanceof Boolean b) {
                return b;
            }
            return "true".equalsIgnoreCase(String.valueOf(v));
        }
        return false;
    }

    private ObjectNode aggregateOnlyPlaceholder(String scope) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("aggregateOnly", true);
        n.put("scope", scope);
        n.put("message", "Row-level payload withheld for this visibility profile.");
        return n;
    }

    private void maybeAttachDagsGovernanceSummary(Map<String, Object> input, ObjectNode result) {
        if (!Boolean.TRUE.equals(input.get("attachDagsGovernanceSummary"))) {
            return;
        }
        try {
            JsonNode pending = dags.listAccessRequests("PENDING", 0, 30);
            int n = countJsonCollection(pending);
            ObjectNode go = result.putObject("governanceSummary");
            go.put("pendingAccessRequestRows", n);
            go.put("source", "dags");
        } catch (Exception e) {
            result.putObject("governanceSummary").put("error", e.getMessage());
        }
    }

    private static int countJsonCollection(JsonNode pending) {
        if (pending == null) {
            return 0;
        }
        if (pending.isArray()) {
            return pending.size();
        }
        if (pending.isObject() && pending.has("content") && pending.get("content").isArray()) {
            return pending.get("content").size();
        }
        return 0;
    }

    private void addGuidanceHits(ArrayNode ranked, JsonNode g, String sourceTag) {
        if (g == null) {
            return;
        }
        if (g.isArray()) {
            for (JsonNode hit : g) {
                ObjectNode o = ranked.addObject();
                o.put("source", sourceTag);
                o.set("hit", hit);
            }
        }
    }

    private static String describeArraySize(JsonNode n) {
        if (n == null) {
            return "no data";
        }
        if (n.isArray()) {
            return n.size() + " rows";
        }
        return "non-array payload";
    }

    private static String stringAttr(Map<String, Object> ctx, String key) {
        return stringAttr(ctx, key, "");
    }

    private static String stringAttr(Map<String, Object> ctx, String key, String def) {
        Object v = ctx.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private void emit(String type, Map<String, Object> payload) {
        IntelligenceEventPublisher publisher = eventPublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publish(type, payload);
        }
    }

    private void structuredAuditLog(
            String tenantId,
            String requestId,
            String correlationId,
            @Nullable String actorId,
            String queryType,
            String queryId,
            String status) {
        log.info(
                "INTELLIGENCE_AUDIT tenant={} requestId={} correlationId={} actorId={} queryType={} queryId={} status={}",
                tenantId,
                requestId,
                correlationId,
                actorId,
                queryType,
                queryId,
                status);
    }
}
