package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.DispatchServiceClient;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkflowServiceClient;

import java.util.List;
import java.util.Map;

@Service
public class CoreTransactionCompositionService {

    private static final Logger log = LoggerFactory.getLogger(CoreTransactionCompositionService.class);
    public static final String ENCOUNTER_TX_PREFIX = "encounter-";
    public static final String JOURNEY_TX_PREFIX = "journey-";
    public static final String ADMISSION_TX_PREFIX = "admission-";

    private final WorkflowServiceClient workflowServiceClient;
    private final PctServiceClient pctServiceClient;
    private final CostaServiceClient costaServiceClient;
    private final DispatchServiceClient dispatchServiceClient;
    private final InpatientServiceClient inpatientServiceClient;
    private final ObjectMapper objectMapper;

    public CoreTransactionCompositionService(WorkflowServiceClient workflowServiceClient,
                                            PctServiceClient pctServiceClient,
                                            CostaServiceClient costaServiceClient,
                                            DispatchServiceClient dispatchServiceClient,
                                            ObjectMapper objectMapper) {
        this(workflowServiceClient, pctServiceClient, costaServiceClient, dispatchServiceClient, null, objectMapper);
    }

    public CoreTransactionCompositionService(WorkflowServiceClient workflowServiceClient,
                                            PctServiceClient pctServiceClient,
                                            CostaServiceClient costaServiceClient,
                                            DispatchServiceClient dispatchServiceClient,
                                            InpatientServiceClient inpatientServiceClient,
                                            ObjectMapper objectMapper) {
        this.workflowServiceClient = workflowServiceClient;
        this.pctServiceClient = pctServiceClient;
        this.costaServiceClient = costaServiceClient;
        this.dispatchServiceClient = dispatchServiceClient;
        this.inpatientServiceClient = inpatientServiceClient;
        this.objectMapper = objectMapper;
    }

    public ObjectNode listCoreTransactions(String state, String type) {
        return listCoreTransactions(state, type, null);
    }

    public ObjectNode listCoreTransactions(String state, String type, String encounterId) {
        ObjectNode envelope = objectMapper.createObjectNode();
        ArrayNode items = objectMapper.createArrayNode();
        ArrayNode failures = objectMapper.createArrayNode();

        try {
            JsonNode workflows = workflowServiceClient.listWorkflows(state);
            if (workflows != null && workflows.isArray()) {
                for (JsonNode node : workflows) {
                    items.add(toCoreTransactionView(node, type));
                }
            } else if (workflows != null && workflows.has("items") && workflows.get("items").isArray()) {
                for (JsonNode node : workflows.get("items")) {
                    items.add(toCoreTransactionView(node, type));
                }
            }
        } catch (Exception ex) {
            log.warn("Core transaction list composition failed: {}", ex.getMessage());
            failures.add("WORKFLOW_UNAVAILABLE");
        }

        appendWorkflowInstanceTransactions(items, failures, state, type);
        appendDeliveryTransactions(items, failures, state, type);

        if (encounterId != null && !encounterId.isBlank()) {
            ArrayNode filtered = objectMapper.createArrayNode();
            for (JsonNode item : items) {
                String linkedEncounter = item.path("clinicalContext").path("encounterId").asText("");
                if (encounterId.equals(linkedEncounter)) {
                    filtered.add(item);
                }
            }
            if (filtered.isEmpty()) {
                ObjectNode encounterBacked = composeFromPctEncounter(encounterId);
                if (encounterBacked != null) {
                    filtered.add(encounterBacked);
                }
            }
            items = filtered;
        }

        envelope.set("items", items);
        envelope.set("failureModes", failures);
        return envelope;
    }

    public ObjectNode getCoreTransaction(String transactionId) {
        if (isEncounterTransactionId(transactionId)) {
            return composeFromPctEncounter(stripEncounterTransactionId(transactionId));
        }
        if (transactionId != null && transactionId.startsWith(JOURNEY_TX_PREFIX)) {
            return composeFromPctJourney(transactionId.substring(JOURNEY_TX_PREFIX.length()));
        }
        if (transactionId != null && transactionId.startsWith(ADMISSION_TX_PREFIX)) {
            return composeFromInpatientAdmission(stripAdmissionTransactionId(transactionId));
        }
        if (transactionId != null && transactionId.startsWith("delivery-")) {
            String deliveryId = transactionId.substring("delivery-".length());
            try {
                JsonNode delivery = dispatchServiceClient.getDelivery(deliveryId);
                if (delivery == null || delivery.isNull()) {
                    return null;
                }
                return toDeliveryCoreTransactionView(delivery);
            } catch (Exception ex) {
                log.warn("Delivery core transaction get failed: {}", ex.getMessage());
                return null;
            }
        }
        JsonNode workflow = workflowServiceClient.getWorkflow(transactionId);
        if (workflow == null || workflow.isMissingNode() || workflow.isNull()) {
            return null;
        }
        return toCoreTransactionView(workflow, null);
    }

    public JsonNode createCoreTransaction(Map<String, Object> payload) {
        return workflowServiceClient.startWorkflow(payload);
    }

    public JsonNode applyAction(String transactionId, String actionCode, Map<String, Object> payload) {
        if (isEncounterTransactionId(transactionId)) {
            return applyEncounterTransactionAction(stripEncounterTransactionId(transactionId), actionCode, payload);
        }
        Map<String, Object> body = Map.of(
                "actionCode", actionCode,
                "payload", payload
        );
        return workflowServiceClient.signalWorkflow(transactionId, body);
    }

    public ArrayNode getTimeline(String transactionId) {
        ObjectNode view = getCoreTransaction(transactionId);
        if (view == null || !view.has("timeline")) {
            return objectMapper.createArrayNode();
        }
        JsonNode timeline = view.get("timeline");
        if (timeline != null && timeline.isArray()) {
            return (ArrayNode) timeline;
        }
        return objectMapper.createArrayNode();
    }

    public ArrayNode getNextActions(String transactionId) {
        ObjectNode view = getCoreTransaction(transactionId);
        if (view == null || !view.has("nextActions")) {
            return objectMapper.createArrayNode();
        }
        JsonNode nextActions = view.get("nextActions");
        if (nextActions != null && nextActions.isArray()) {
            return (ArrayNode) nextActions;
        }
        return objectMapper.createArrayNode();
    }

    public ObjectNode getJourneys(String transactionId) {
        ObjectNode view = getCoreTransaction(transactionId);
        if (view == null || !view.has("journeys")) {
            return objectMapper.createObjectNode();
        }
        JsonNode journeys = view.get("journeys");
        if (journeys != null && journeys.isObject()) {
            return (ObjectNode) journeys;
        }
        return objectMapper.createObjectNode();
    }

    public ObjectNode getNompilo(String transactionId) {
        ObjectNode view = getCoreTransaction(transactionId);
        if (view == null || !view.has("nompilo")) {
            return objectMapper.createObjectNode();
        }
        JsonNode nompilo = view.get("nompilo");
        if (nompilo != null && nompilo.isObject()) {
            return (ObjectNode) nompilo;
        }
        return objectMapper.createObjectNode();
    }

    public ObjectNode submitFeedback(String transactionId, Map<String, Object> payload) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("transactionId", transactionId);
        response.put("status", "ACCEPTED");
        response.put("message", "Feedback accepted for core transaction learning loop.");
        response.set("payload", objectMapper.valueToTree(payload));
        return response;
    }

    public ObjectNode requestNompiloHandoff(String transactionId, Map<String, Object> payload) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("transactionId", transactionId);
        response.put("status", "ACCEPTED");
        response.put("message", "Nompilo handoff request accepted.");
        response.set("payload", objectMapper.valueToTree(payload));
        return response;
    }

    public ObjectNode executeNompiloCommand(String transactionId, Map<String, Object> payload) {
        ObjectNode response = objectMapper.createObjectNode();
        String query = payload == null ? "" : String.valueOf(payload.getOrDefault("query", ""));
        response.put("transactionId", transactionId);
        response.put("status", "ACCEPTED");
        response.put("message", "Nompilo command accepted for governed orchestration.");
        response.put("intent", classifyNompiloIntent(query));
        response.put("authoritativeSource", "NOMPILO_KNOWLEDGE_BASE");
        response.put("sourceOfTruth", false);
        response.put("safetyNotice", "Nompilo composes guidance only; source services remain authoritative.");
        response.set("payload", objectMapper.valueToTree(payload));
        return response;
    }

    private ObjectNode toCoreTransactionView(JsonNode workflowNode, String typeOverride) {
        ObjectNode root = objectMapper.createObjectNode();
        String transactionId = stringOr(workflowNode, "id", "tx-unknown");
        String currentState = stringOr(workflowNode, "state", "INITIATED");
        String transactionType = typeOverride != null && !typeOverride.isBlank()
                ? typeOverride
                : stringOr(workflowNode, "transactionType", "FACILITY_WALK_IN");

        ObjectNode transaction = objectMapper.createObjectNode();
        transaction.put("id", transactionId);
        transaction.put("type", transactionType);
        transaction.put("currentState", currentState);
        transaction.put("lifecycleStage", lifecycleStageForState(currentState));
        transaction.put("serviceCode", stringOr(workflowNode, "serviceCode", "svc.core.transaction"));
        root.set("transaction", transaction);

        ObjectNode clientSummary = objectMapper.createObjectNode();
        String clientRef = stringOr(workflowNode, "clientRef", null);
        String clientAlias = stringOr(workflowNode, "clientAlias", null);
        if (clientRef != null) clientSummary.put("clientRef", clientRef);
        if (clientAlias != null) clientSummary.put("clientAlias", clientAlias);
        clientSummary.put("identityStatus", stringOr(workflowNode, "identityStatus", "IDENTITY_PENDING"));
        root.set("clientSummary", clientSummary);

        ObjectNode providerContext = objectMapper.createObjectNode();
        providerContext.put("actorId", stringOr(workflowNode, "actorId", "SYSTEM"));
        providerContext.put("providerRef", stringOr(workflowNode, "providerRef", "varapi:unknown"));
        root.set("providerContext", providerContext);

        ObjectNode facilityContext = objectMapper.createObjectNode();
        facilityContext.put("facilityId", stringOr(workflowNode, "facilityId", "tuso:unknown"));
        facilityContext.put("workspaceId", stringOr(workflowNode, "workspaceId", "workspace:default"));
        root.set("facilityContext", facilityContext);

        ObjectNode trustContext = objectMapper.createObjectNode();
        trustContext.put("purposeOfUse", stringOr(workflowNode, "purposeOfUse", "TREATMENT"));
        trustContext.put("consentStatus", stringOr(workflowNode, "consentStatus", "PENDING"));
        trustContext.put("accessStatus", stringOr(workflowNode, "accessStatus", "ALLOW"));
        root.set("trustContext", trustContext);

        ObjectNode clinicalContext = objectMapper.createObjectNode();
        String cpid = stringOr(workflowNode, "cpid", null);
        clinicalContext.put("encounterId", stringOr(workflowNode, "encounterId", ""));
        clinicalContext.put("ordersPending", 0);
        clinicalContext.put("resultsPending", 0);
        root.set("clinicalContext", clinicalContext);

        ObjectNode financialContext = objectMapper.createObjectNode();
        financialContext.put("costingStatus", "PENDING");
        financialContext.put("paymentStatus", "PENDING");
        financialContext.put("claimStatus", "NOT_APPLICABLE");
        root.set("financialContext", financialContext);

        ObjectNode followUp = objectMapper.createObjectNode();
        followUp.put("required", !"COMPLETED".equals(currentState) && !"CLOSED".equals(currentState));
        followUp.put("note", "Follow-up is derived from transaction state and clinical completion status.");
        root.set("followUp", followUp);
        ArrayNode timeline = objectMapper.createArrayNode();
        timeline.add(timelineNode("core.transaction.initiated", "DRAFT", "INITIATED", "COMPLETED"));
        timeline.add(timelineNode("core.trust.context.established", "IDENTITY_RESOLVED", "TRUST_CONTEXT_ESTABLISHED", "CURRENT"));
        if (cpid != null && !cpid.isBlank()) {
            try {
                JsonNode pctTimeline = pctServiceClient.getPatientTimeline(cpid);
                if (pctTimeline != null && pctTimeline.isArray()) {
                    for (JsonNode item : pctTimeline) {
                        ObjectNode node = objectMapper.createObjectNode();
                        node.put("eventName", "core.encounter.updated");
                        node.put("stateBefore", "IN_SERVICE");
                        node.put("stateAfter", "IN_SERVICE");
                        node.put("status", "COMPLETED");
                        node.put("actorId", "PCT");
                        node.put("at", stringOr(item, "updatedAt", ""));
                        timeline.add(node);
                    }
                }
            } catch (Exception ex) {
                log.debug("PCT timeline unavailable for cpid={}: {}", cpid, ex.getMessage());
            }
        }
        root.set("timeline", timeline);

        ArrayNode nextActions = objectMapper.createArrayNode();
        for (Map<String, String> action : nextActionsForState(currentState)) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("code", action.get("code"));
            node.put("label", action.get("label"));
            nextActions.add(node);
        }
        root.set("nextActions", nextActions);

        ArrayNode permissions = objectMapper.createArrayNode();
        permissions.add(permission("core.transaction.view", true));
        permissions.add(permission("core.transaction.advance", true));
        root.set("permissions", permissions);

        ObjectNode auditSummary = objectMapper.createObjectNode();
        auditSummary.put("correlationId", stringOr(workflowNode, "correlationId", "unknown"));
        auditSummary.put("sourceSystem", "experience-bff");
        auditSummary.put("auditRequired", true);
        root.set("auditSummary", auditSummary);

        String offlineSyncStatus = stringOr(workflowNode, "offlineSyncStatus", "ONLINE");
        root.put("offlineSyncStatus", offlineSyncStatus);
        ArrayNode failureModes = objectMapper.createArrayNode();
        deriveFailureModes(
                currentState,
                offlineSyncStatus,
                stringOr(workflowNode, "accessStatus", "ALLOW"),
                stringOr(workflowNode, "consentStatus", "PENDING")
        ).forEach(failureModes::add);
        List<String> blockerReasons = deriveBlockerReasons(currentState, workflowNode);
        root.set("failureModes", failureModes);
        root.set("blockerReasons", objectMapper.valueToTree(blockerReasons));

        // Keep financial context composition anchored to COSTA where possible.
        try {
            JsonNode bills = costaServiceClient.listBills(0, 1, null);
            if (bills != null) {
                financialContext.put("costingStatus", "CALCULATED");
                String billStatus = extractFirstBillStatus(bills);
                if (billStatus != null && !billStatus.isBlank()) {
                    financialContext.put("billingStatus", billStatus);
                    if ("FINAL".equalsIgnoreCase(billStatus) || "PAID".equalsIgnoreCase(billStatus)) {
                        financialContext.put("paymentStatus", "PAID");
                    }
                }
            }
        } catch (Exception ex) {
            failureModes.add("FINANCIAL_CONTEXT_UNAVAILABLE");
            blockerReasons.add("Financial context unavailable from COSTA.");
        }

        root.set("journeys", buildJourneyViews(currentState, timeline, nextActionsForState(currentState), failureModes, blockerReasons));
        root.set("nompilo", buildNompiloContext(currentState, failureModes, blockerReasons));
        root.set("nompiloCommand", buildNompiloCommandContext(workflowNode, currentState));

        return root;
    }

    private ObjectNode timelineNode(String eventName, String before, String after, String status) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("eventName", eventName);
        node.put("stateBefore", before);
        node.put("stateAfter", after);
        node.put("status", status);
        node.put("actorId", "SYSTEM");
        node.put("at", "");
        return node;
    }

    private ObjectNode permission(String code, boolean allowed) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("code", code);
        node.put("allowed", allowed);
        return node;
    }

    private ObjectNode buildJourneyViews(String currentState,
                                         ArrayNode timeline,
                                         List<Map<String, String>> nextActions,
                                         ArrayNode failureModes,
                                         List<String> blockerReasons) {
        ObjectNode journeys = objectMapper.createObjectNode();
        journeys.set("person", buildJourneyNode(
                "PERSON",
                personStageForState(currentState),
                List.of("CLIENT", "PROVIDER"),
                timeline,
                nextActions,
                failureModes,
                blockerReasons
        ));
        journeys.set("provider", buildJourneyNode(
                "PROVIDER",
                providerStageForState(currentState),
                List.of("PROVIDER", "PLATFORM_OPERATOR"),
                timeline,
                nextActions,
                failureModes,
                blockerReasons
        ));
        journeys.set("platform", buildJourneyNode(
                "PLATFORM",
                platformStageForState(currentState),
                List.of("PLATFORM_OPERATOR"),
                timeline,
                nextActions,
                failureModes,
                blockerReasons
        ));
        return journeys;
    }

    private ObjectNode buildJourneyNode(String journeyType,
                                        String currentStage,
                                        List<String> visibleToActor,
                                        ArrayNode timeline,
                                        List<Map<String, String>> nextActions,
                                        ArrayNode failureModes,
                                        List<String> blockerReasons) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("journeyType", journeyType);
        node.put("currentStage", currentStage);
        ArrayNode stages = objectMapper.createArrayNode();
        stages.add(currentStage);
        node.set("stages", stages);
        ArrayNode visible = objectMapper.createArrayNode();
        visibleToActor.forEach(visible::add);
        node.set("visibleToActor", visible);
        ArrayNode blockers = objectMapper.createArrayNode();
        failureModes.forEach(blockers::add);
        node.set("blockers", blockers);
        node.set("blockerReasons", objectMapper.valueToTree(blockerReasons));
        node.put("completionStatus", "IN_PROGRESS");
        node.set("timelineEntries", timeline);
        ArrayNode actions = objectMapper.createArrayNode();
        for (Map<String, String> action : nextActions) {
            actions.add(objectMapper.createObjectNode()
                    .put("code", action.get("code"))
                    .put("label", action.get("label")));
        }
        node.set("nextActions", actions);
        return node;
    }

    private ObjectNode buildNompiloContext(String currentState, ArrayNode failureModes, List<String> blockerReasons) {
        ObjectNode nompilo = objectMapper.createObjectNode();
        nompilo.put("available", true);
        nompilo.put("mode", nompiloModeForState(currentState));
        nompilo.put("confidence", failureModes.isEmpty() ? "HIGH" : "MEDIUM");
        ArrayNode guidance = objectMapper.createArrayNode().add(
                objectMapper.createObjectNode()
                        .put("id", "guidance-primary")
                        .put("surfaceType", "FLOATING_ASSISTANT")
                        .put("message", "Current state is " + currentState + ". I can explain the next safe action.")
        );
        if (!blockerReasons.isEmpty()) {
            guidance.add(objectMapper.createObjectNode()
                    .put("id", "guidance-blocker")
                    .put("surfaceType", "SMART_NUDGE")
                    .put("message", blockerReasons.get(0)));
        }
        nompilo.set("currentGuidance", guidance);
        nompilo.set("suggestedQuestions", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode()
                        .put("code", "WHAT_NEXT")
                        .put("prompt", "What happens next in this journey?")
        ));
        nompilo.set("nextBestActions", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode()
                        .put("code", "VIEW_TIMELINE")
                        .put("label", "Review timeline and next actions")
        ));
        if (!failureModes.isEmpty()) {
            nompilo.withArray("nextBestActions").add(
                    objectMapper.createObjectNode()
                            .put("code", "RECOVER_BLOCKER")
                            .put("label", "Recover from active blocker")
            );
        }
        nompilo.set("accessibilityOptions", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("code", "SIMPLE_LANGUAGE").put("enabled", true))
                .add(objectMapper.createObjectNode().put("code", "READ_ALOUD_AUDIO_GUIDANCE").put("enabled", true)));
        nompilo.set("feedbackPrompts", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode()
                        .put("id", "feedback-in-app")
                        .put("channel", "IN_APP")
                        .put("prompt", "How easy was this step?")
        ));
        nompilo.set("handoffOptions", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode()
                        .put("id", "handoff-helpdesk")
                        .put("label", "Talk to facility help desk")
                        .put("destination", "FACILITY_HELP_DESK")
        ));
        nompilo.set("privacyNotice", objectMapper.createObjectNode()
                .put("summary", "Nompilo guidance respects trust, consent, and access policy."));
        return nompilo;
    }

    private ObjectNode buildNompiloCommandContext(JsonNode workflowNode, String currentState) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("enabled", true);
        command.set("surfaces", objectMapper.valueToTree(List.of(
                "GLOBAL_SEARCH_BAR",
                "COMMAND_PALETTE",
                "FLOATING_ASSISTANT",
                "VOICE_DICTATION",
                "INLINE_JOURNEY_PROMPT",
                "FUNDO_LEARNING_ASSISTANT",
                "DASHBOARD_ASSISTANT",
                "PROVIDER_WORKSPACE_ASSISTANT",
                "CLIENT_APP_ASSISTANT",
                "KIOSK_ASSISTANT",
                "SUPPORT_HELP_INTERFACE",
                "MOBILE_CHAT",
                "MOBILE_VOICE"
        )));
        command.set("availableSearchSources", objectMapper.valueToTree(List.of(
                "VITO_CLIENT_REGISTRY",
                "VARAPI_PROVIDER_REGISTRY",
                "TUSO_FACILITY_REGISTRY",
                "MSIKA_SERVICE_PRODUCT_CATALOGUE",
                "MSIKA_FLOW_FULFILMENT",
                "INDAWO_PUBLIC_HEALTH_SITES",
                "ZIBO_TERMINOLOGY",
                "FUNDO_LEARNING",
                "BUTANO_CLINICAL_SUMMARY",
                "COSTA_COSTING",
                "MUSHEX_PAYMENTS_CLAIMS",
                "DATA_PLANE_ANALYTICS",
                "DOCUMENT_SERVICE",
                "SUPPORT_HELPDESK",
                "NOMPILO_KNOWLEDGE_BASE"
        )));
        command.set("suggestedCommands", objectMapper.valueToTree(List.of(
                "FIND_SERVICE",
                "FIND_PROVIDER",
                "ASK_WORKFLOW_HELP",
                "ASK_PAYMENT_OR_CLAIM_HELP",
                "REQUEST_SUPPORT",
                "REQUEST_LOGIN_BRIEFING"
        )));
        command.set("recentCommands", objectMapper.valueToTree(List.of("CHECK_STATUS", "REQUEST_SUMMARY")));
        command.put("voiceEnabled", true);
        command.put("dictationEnabled", true);
        ArrayNode toolPermissions = objectMapper.createArrayNode();
        toolPermissions.add(objectMapper.createObjectNode().put("source", "VITO_CLIENT_REGISTRY").put("allowed", true));
        toolPermissions.add(objectMapper.createObjectNode().put("source", "VARAPI_PROVIDER_REGISTRY").put("allowed", true));
        toolPermissions.add(objectMapper.createObjectNode().put("source", "BUTANO_CLINICAL_SUMMARY").put("allowed", true));
        toolPermissions.add(objectMapper.createObjectNode()
                .put("source", "GOVERNED_INTERNET_SEARCH")
                .put("allowed", false)
                .put("reason", "External search is policy-controlled and role-restricted."));
        command.set("toolPermissions", toolPermissions);
        ObjectNode loginBriefing = objectMapper.createObjectNode();
        loginBriefing.put("role", "PROVIDER");
        loginBriefing.put("headline", "Nompilo daily briefing for current role.");
        loginBriefing.set("highlights", objectMapper.valueToTree(List.of(
                "Current workflow state: " + currentState,
                "Provider queue and pending tasks available"
        )));
        loginBriefing.set("urgentItems", objectMapper.valueToTree(List.of(
                "Review blockers before progressing transaction"
        )));
        command.set("loginBriefing", loginBriefing);
        command.set("supportHandoff", objectMapper.createObjectNode()
                .put("enabled", true)
                .put("destination", "FACILITY_HELP_DESK")
                .put("queue", "tier-1"));
        command.set("analyticsCapabilities", objectMapper.valueToTree(List.of(
                "Generate dashboard views",
                "Compile briefing summaries",
                "Highlight data quality issues"
        )));
        command.set("learningCapabilities", objectMapper.valueToTree(List.of(
                "Explain Fundo modules",
                "Launch quiz support",
                "Suggest competency refreshers"
        )));
        return command;
    }

    static List<String> deriveBlockerReasons(String state, JsonNode workflowNode) {
        List<String> reasons = new java.util.ArrayList<>();
        addIfPresent(reasons, workflowNode, "paymentFailureReason");
        addIfPresent(reasons, workflowNode, "syncFailureReason");
        addIfPresent(reasons, workflowNode, "accessDeniedReason");
        addIfPresent(reasons, workflowNode, "consentDeniedReason");
        addIfPresent(reasons, workflowNode, "claimRejectionReason");
        addIfPresent(reasons, workflowNode, "documentationGap");
        if ("PRE_SERVICE_PAYMENT_REQUIRED".equals(state)) {
            reasons.add("Pre-service payment or exemption confirmation is required.");
        }
        if ("PENDING_SYNC".equals(state)) {
            reasons.add("Offline sync is pending reconciliation.");
        }
        return reasons;
    }

    private static String lifecycleStageForState(String state) {
        return switch (state) {
            case "DRAFT", "INITIATED" -> "ENTRY";
            case "IDENTITY_PENDING", "IDENTITY_RESOLVED", "PROVISIONAL_IDENTITY" -> "IDENTITY";
            case "TRUST_CONTEXT_ESTABLISHED" -> "TRUST_AND_CONSENT";
            case "SERVICE_SELECTED" -> "SERVICE_SELECTION";
            case "SCHEDULED", "QUEUED", "TASKED" -> "SCHEDULING_OR_QUEUE";
            case "TRIAGE_IN_PROGRESS" -> "TRIAGE_OR_ELIGIBILITY";
            case "READY_FOR_PROVIDER" -> "PROVIDER_ASSIGNMENT";
            case "IN_SERVICE", "EMERGENCY_OVERRIDE" -> "ENCOUNTER_OR_DELIVERY";
            case "ORDERS_PENDING", "ANCILLARY_IN_PROGRESS", "PENDING_RESULT" -> "ORDERS_AND_ANCILLARY";
            case "FINANCIAL_PROCESSING", "PENDING_PAYMENT", "PENDING_CLAIM" -> "FINANCIAL_SETTLEMENT";
            case "SHR_UPDATE_PENDING" -> "RECORD_CONTRIBUTION";
            case "CLIENT_INSTRUCTIONS_PENDING" -> "INSTRUCTIONS_AND_NOTIFICATIONS";
            case "FOLLOW_UP_ACTIVE", "REFERRED_OUT" -> "FOLLOW_UP_CONTINUITY";
            default -> "COMPLETION";
        };
    }

    private static List<Map<String, String>> nextActionsForState(String state) {
        return switch (state) {
            case "IDENTITY_PENDING" -> List.of(
                    Map.of("code", "RESOLVE_IDENTITY", "label", "Resolve client identity"),
                    Map.of("code", "CREATE_PROVISIONAL_IDENTITY", "label", "Create provisional identity")
            );
            case "TRUST_CONTEXT_ESTABLISHED" -> List.of(
                    Map.of("code", "SELECT_SERVICE", "label", "Select service from governed catalog"),
                    Map.of("code", "CHECK_CONSENT", "label", "Check consent and purpose of use")
            );
            case "QUEUED", "TASKED" -> List.of(
                    Map.of("code", "START_TRIAGE", "label", "Start triage"),
                    Map.of("code", "ASSIGN_PROVIDER", "label", "Assign provider")
            );
            case "IN_SERVICE" -> List.of(
                    Map.of("code", "CREATE_ORDER", "label", "Create order"),
                    Map.of("code", "COMPLETE_ENCOUNTER", "label", "Complete encounter")
            );
            case "FINANCIAL_PROCESSING" -> List.of(
                    Map.of("code", "INITIATE_PAYMENT", "label", "Initiate payment"),
                    Map.of("code", "SUBMIT_CLAIM", "label", "Submit claim")
            );
            default -> List.of(
                    Map.of("code", "VIEW_TIMELINE", "label", "Review transaction timeline")
            );
        };
    }

    static List<String> deriveFailureModes(String state,
                                           String offlineSyncStatus,
                                           String accessStatus,
                                           String consentStatus) {
        List<String> failures = new java.util.ArrayList<>();
        if ("PENDING_SYNC".equals(state) || "SYNC_PENDING".equals(offlineSyncStatus)) {
            failures.add("OFFLINE_SYNC_PENDING");
        }
        if ("FAILED_SYNC".equals(state) || "SYNC_FAILED".equals(offlineSyncStatus)) {
            failures.add("SYNC_FAILED");
        }
        if ("PRE_SERVICE_PAYMENT_FAILED".equals(state) || "PAYMENT_FAILED".equals(state)) {
            failures.add("PRE_SERVICE_PAYMENT_FAILED");
            failures.add("PAYMENT_FAILED");
        }
        if ("CLAIM_REJECTED".equals(state)) {
            failures.add("CLAIM_REJECTED");
        }
        if ("DENY".equalsIgnoreCase(accessStatus) || "ACCESS_DENIED".equals(state)) {
            failures.add("ACCESS_DENIED");
        }
        if ("DENIED".equalsIgnoreCase(consentStatus) || "CONSENT_DENIED".equals(state)) {
            failures.add("CONSENT_DENIED");
        }
        return failures;
    }

    static String nompiloModeForState(String state) {
        return switch (state) {
            case "PENDING_SYNC", "FAILED_SYNC" -> "OPERATIONS_INSIGHT";
            case "PRE_SERVICE_PAYMENT_REQUIRED", "PRE_SERVICE_PAYMENT_PENDING", "PRE_SERVICE_PAYMENT_FAILED" -> "STEP_BY_STEP_GUIDE";
            case "CONSENT_DENIED", "ACCESS_DENIED" -> "HUMAN_HANDOFF";
            default -> "CONTEXTUAL_COMPANION";
        };
    }

    static String classifyNompiloIntent(String query) {
        String normalized = query == null ? "" : query.toLowerCase();
        if (normalized.contains("service")) return "FIND_SERVICE";
        if (normalized.contains("provider") || normalized.contains("doctor")) return "FIND_PROVIDER";
        if (normalized.contains("facility")) return "FIND_FACILITY";
        if (normalized.contains("commodity") || normalized.contains("stock")) return "FIND_COMMODITY";
        if (normalized.contains("wellness") || normalized.contains("campaign")) return "FIND_WELLNESS_EVENT";
        if (normalized.contains("dashboard")) return "REQUEST_DASHBOARD";
        if (normalized.contains("report")) return "REQUEST_REPORT";
        if (normalized.contains("support") || normalized.contains("ticket") || normalized.contains("handoff")) return "REQUEST_SUPPORT";
        if (normalized.contains("quiz")) return "REQUEST_QUIZ";
        if (normalized.contains("fundo") || normalized.contains("module")) return "ASK_FUNDO_HELP";
        if (normalized.contains("voice") || normalized.contains("dictate")) return "DICTATE_NEED";
        return "ASK_WORKFLOW_HELP";
    }

    private static String extractFirstBillStatus(JsonNode bills) {
        if (bills == null || bills.isNull()) return null;
        JsonNode content = bills.path("content");
        if (content.isArray() && !content.isEmpty()) {
            return content.get(0).path("status").asText(null);
        }
        if (bills.isArray() && !bills.isEmpty()) {
            return bills.get(0).path("status").asText(null);
        }
        return null;
    }

    private static void addIfPresent(List<String> reasons, JsonNode node, String field) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            String value = node.get(field).asText();
            if (value != null && !value.isBlank()) {
                reasons.add(value);
            }
        }
    }

    private static String personStageForState(String state) {
        return switch (state) {
            case "DRAFT", "INITIATED" -> "FIND_CARE";
            case "IDENTITY_PENDING", "IDENTITY_RESOLVED", "PROVISIONAL_IDENTITY" -> "IDENTIFY_ME";
            case "SERVICE_SELECTED" -> "SELECT_SERVICE";
            case "FINANCIAL_PROCESSING", "PENDING_PAYMENT", "PENDING_CLAIM", "PAYMENT_FAILED", "CLAIM_REJECTED" ->
                    "SETTLE_ADDITIONAL_CHARGES_OR_CLAIMS";
            case "FOLLOW_UP_ACTIVE", "REFERRED_OUT" -> "CONTINUE_CARE";
            default -> "RECEIVE_CARE";
        };
    }

    private static String providerStageForState(String state) {
        return switch (state) {
            case "DRAFT", "INITIATED" -> "START_DUTY";
            case "SCHEDULED", "QUEUED", "TASKED" -> "SEE_MY_WORK";
            case "TRIAGE_IN_PROGRESS", "READY_FOR_PROVIDER" -> "PREPARE_TRIAGE_OR_SCREEN";
            case "IN_SERVICE", "EMERGENCY_OVERRIDE" -> "DELIVER_CARE";
            case "FINANCIAL_PROCESSING", "PENDING_PAYMENT", "PENDING_CLAIM" -> "CONFIRM_CHARGES_CLAIMS_OR_FOLLOW_UP";
            default -> "COMPLETE_TRANSACTION";
        };
    }

    private static String platformStageForState(String state) {
        return switch (state) {
            case "INITIATED" -> "RECEIVE_TRIGGER";
            case "IDENTITY_PENDING", "IDENTITY_RESOLVED", "PROVISIONAL_IDENTITY" -> "RESOLVE_IDENTITY";
            case "TRUST_CONTEXT_ESTABLISHED" -> "ESTABLISH_TRUST_CONTEXT";
            case "SERVICE_SELECTED", "SCHEDULED", "QUEUED", "TASKED" -> "RESOLVE_SERVICE_AND_WORKFLOW";
            case "FINANCIAL_PROCESSING", "PENDING_PAYMENT", "PENDING_CLAIM", "PAYMENT_FAILED", "CLAIM_REJECTED" ->
                    "EXECUTE_FINANCIAL_OR_ENTERPRISE_FLOW";
            case "PENDING_SYNC", "FAILED_SYNC", "PENDING_RECONCILIATION" -> "HANDLE_FAILURE_OFFLINE_AND_RECONCILIATION";
            default -> "MANAGE_STATE_MACHINE";
        };
    }

    public static String encounterTransactionId(String encounterId) {
        return ENCOUNTER_TX_PREFIX + encounterId;
    }

    public static String journeyTransactionId(String journeyId) {
        return JOURNEY_TX_PREFIX + journeyId;
    }

    public static String admissionTransactionId(String admissionRef) {
        return ADMISSION_TX_PREFIX + admissionRef;
    }

    public static boolean isAdmissionTransactionId(String transactionId) {
        return transactionId != null && transactionId.startsWith(ADMISSION_TX_PREFIX);
    }

    private static String stripAdmissionTransactionId(String transactionId) {
        return transactionId.substring(ADMISSION_TX_PREFIX.length());
    }

    public static boolean isEncounterTransactionId(String transactionId) {
        return transactionId != null && transactionId.startsWith(ENCOUNTER_TX_PREFIX);
    }

    private static String stripEncounterTransactionId(String transactionId) {
        return transactionId.substring(ENCOUNTER_TX_PREFIX.length());
    }

    private JsonNode applyEncounterTransactionAction(String encounterId, String actionCode, Map<String, Object> payload) {
        if ("COMPLETE_ENCOUNTER".equalsIgnoreCase(actionCode)) {
            try {
                long encId = Long.parseLong(encounterId.trim());
                pctServiceClient.completeEncounter(encId);
            } catch (Exception ex) {
                log.warn("Encounter completion via core-transaction action failed: {}", ex.getMessage());
                throw new IllegalStateException("Unable to complete encounter " + encounterId + ": " + ex.getMessage(), ex);
            }
        }
        ObjectNode view = composeFromPctEncounter(encounterId);
        if (view == null) {
            throw new IllegalStateException("Encounter transaction not found for encounter " + encounterId);
        }
        return view;
    }

    private ObjectNode composeFromPctEncounter(String encounterId) {
        if (encounterId == null || encounterId.isBlank()) {
            return null;
        }
        try {
            long encId = Long.parseLong(encounterId.trim());
            JsonNode pctEncounter = pctServiceClient.getEncounter(encId);
            if (pctEncounter == null || pctEncounter.isNull()) {
                return null;
            }
            ObjectNode pseudo = objectMapper.createObjectNode();
            pseudo.put("id", encounterTransactionId(encounterId));
            pseudo.put("state", mapEncounterStatusToTransactionState(stringOr(pctEncounter, "status", "IN_SERVICE")));
            pseudo.put("transactionType", "FACILITY_WALK_IN");
            pseudo.put("serviceCode", "svc.pct.encounter");
            pseudo.put("encounterId", encounterId);
            pseudo.put("journeyId", stringOr(pctEncounter, "journeyId", ""));
            pseudo.put("cpid", stringOr(pctEncounter, "subjectCpid", stringOr(pctEncounter, "patientCpid", "")));
            pseudo.put("facilityId", stringOr(pctEncounter, "facilityId", "tuso:unknown"));
            pseudo.put("correlationId", stringOr(pctEncounter, "encounterRef", encounterId));
            ObjectNode view = toCoreTransactionView(pseudo, "FACILITY_WALK_IN");
            ObjectNode audit = (ObjectNode) view.get("auditSummary");
            if (audit != null) {
                audit.put("sourceSystem", "pct-service");
            }
            return view;
        } catch (NumberFormatException ex) {
            log.debug("Invalid encounter id for core-transaction composition: {}", encounterId);
            return null;
        } catch (Exception ex) {
            log.warn("PCT encounter composition failed for {}: {}", encounterId, ex.getMessage());
            return null;
        }
    }

    private ObjectNode composeFromInpatientAdmission(String admissionRef) {
        if (admissionRef == null || admissionRef.isBlank()) {
            return null;
        }
        try {
            JsonNode admission = inpatientServiceClient != null
                    ? inpatientServiceClient.getAdmission(admissionRef)
                    : null;
            ObjectNode pseudo = objectMapper.createObjectNode();
            pseudo.put("id", admissionTransactionId(admissionRef));
            String status = admission != null ? stringOr(admission, "status", "ADMITTED") : "ADMITTED";
            pseudo.put("state", mapEncounterStatusToTransactionState(status));
            pseudo.put("transactionType", "EMERGENCY");
            pseudo.put("serviceCode", "svc.inpatient.admission");
            pseudo.put("admissionRef", admissionRef);
            pseudo.put("cpid", admission != null ? stringOr(admission, "patientCpid", stringOr(admission, "cpid", "")) : "");
            pseudo.put("facilityId", admission != null ? stringOr(admission, "facilityId", "tuso:unknown") : "tuso:unknown");
            pseudo.put("correlationId", admissionRef);
            ObjectNode view = toCoreTransactionView(pseudo, "EMERGENCY");
            ObjectNode audit = (ObjectNode) view.get("auditSummary");
            if (audit != null) {
                audit.put("sourceSystem", "inpatient-service");
            }
            return view;
        } catch (Exception ex) {
            log.warn("Inpatient admission composition failed for {}: {}", admissionRef, ex.getMessage());
            return null;
        }
    }

    private ObjectNode composeFromPctJourney(String journeyId) {
        if (journeyId == null || journeyId.isBlank()) {
            return null;
        }
        try {
            JsonNode journey = pctServiceClient.getJourney(journeyId);
            if (journey == null || journey.isNull()) {
                return null;
            }
            ObjectNode pseudo = objectMapper.createObjectNode();
            pseudo.put("id", journeyTransactionId(journeyId));
            pseudo.put("state", mapJourneyStateToTransactionState(stringOr(journey, "state", "QUEUED")));
            pseudo.put("transactionType", "FACILITY_WALK_IN");
            pseudo.put("serviceCode", "svc.pct.journey");
            pseudo.put("journeyId", journeyId);
            pseudo.put("cpid", stringOr(journey, "patientCpid", ""));
            pseudo.put("facilityId", stringOr(journey, "facilityId", "tuso:unknown"));
            pseudo.put("correlationId", journeyId);
            ObjectNode view = toCoreTransactionView(pseudo, "FACILITY_WALK_IN");
            ObjectNode audit = (ObjectNode) view.get("auditSummary");
            if (audit != null) {
                audit.put("sourceSystem", "pct-service");
            }
            return view;
        } catch (Exception ex) {
            log.warn("PCT journey composition failed for {}: {}", journeyId, ex.getMessage());
            return null;
        }
    }

    private static String mapEncounterStatusToTransactionState(String status) {
        if (status == null) {
            return "IN_SERVICE";
        }
        return switch (status.toUpperCase()) {
            case "COMPLETED", "CLOSED", "DISCHARGED" -> "COMPLETED";
            case "CANCELLED" -> "CANCELLED";
            case "STARTED", "ACTIVE", "IN_PROGRESS" -> "IN_SERVICE";
            default -> "IN_SERVICE";
        };
    }

    private static String mapJourneyStateToTransactionState(String state) {
        if (state == null) {
            return "QUEUED";
        }
        return switch (state.toUpperCase()) {
            case "ARRIVED", "WAITING" -> "QUEUED";
            case "IN_SERVICE" -> "IN_SERVICE";
            case "COMPLETED", "CLOSED" -> "COMPLETED";
            default -> "QUEUED";
        };
    }

    private void appendWorkflowInstanceTransactions(ArrayNode items, ArrayNode failures, String state, String type) {
        if (type != null && !type.isBlank() && !"FACILITY_WALK_IN".equalsIgnoreCase(type)) {
            return;
        }
        try {
            JsonNode raw = workflowServiceClient.listInstances(state);
            for (JsonNode instance : asNodeArray(raw)) {
                ObjectNode pseudo = objectMapper.createObjectNode();
                String instanceId = stringOr(instance, "instanceId", stringOr(instance, "id", "unknown"));
                pseudo.put("id", instanceId);
                pseudo.put("state", stringOr(instance, "status", stringOr(instance, "currentStep", "INITIATED")));
                pseudo.put("transactionType", "FACILITY_WALK_IN");
                pseudo.put("serviceCode", "svc.workflow.instance");
                JsonNode context = instance.path("context");
                if (context.isObject()) {
                    pseudo.put("encounterId", stringOr(context, "encounterId", ""));
                    pseudo.put("journeyId", stringOr(context, "journeyId", ""));
                    pseudo.put("cpid", stringOr(context, "cpid", stringOr(context, "patientCpid", "")));
                    pseudo.put("facilityId", stringOr(context, "facilityId", "tuso:unknown"));
                }
                items.add(toCoreTransactionView(pseudo, "FACILITY_WALK_IN"));
            }
        } catch (Exception ex) {
            log.debug("Workflow instance bridge skipped: {}", ex.getMessage());
            failures.add("WORKFLOW_INSTANCES_UNAVAILABLE");
        }
    }

    private void appendDeliveryTransactions(ArrayNode items, ArrayNode failures, String state, String type) {
        if (type != null && !type.isBlank() && !"DELIVERY".equalsIgnoreCase(type)) {
            return;
        }
        try {
            JsonNode raw = dispatchServiceClient.listDeliveries();
            for (JsonNode delivery : asNodeArray(raw)) {
                String status = stringOr(delivery, "status", stringOr(delivery, "state", "UNKNOWN"));
                if (state != null && !state.isBlank() && !state.equalsIgnoreCase(status)) {
                    continue;
                }
                items.add(toDeliveryCoreTransactionView(delivery));
            }
        } catch (Exception ex) {
            log.warn("Dispatch delivery bridge skipped: {}", ex.getMessage());
            failures.add("DISPATCH_UNAVAILABLE");
        }
    }

    private ObjectNode toDeliveryCoreTransactionView(JsonNode delivery) {
        String rawId = stringOr(delivery, "id", stringOr(delivery, "deliveryId", "unknown"));
        String status = stringOr(delivery, "status", stringOr(delivery, "state", "UNKNOWN"));
        ObjectNode pseudo = objectMapper.createObjectNode();
        pseudo.put("id", "delivery-" + rawId);
        pseudo.put("state", status);
        pseudo.put("transactionType", "DELIVERY");
        pseudo.put("serviceCode", "svc.dispatch.delivery");
        pseudo.put("facilityId", stringOr(delivery, "facilityId", "tuso:unknown"));
        ObjectNode view = toCoreTransactionView(pseudo, "DELIVERY");
        ObjectNode audit = (ObjectNode) view.get("auditSummary");
        if (audit != null) {
            audit.put("sourceSystem", "dispatch-service");
        }
        return view;
    }

    private static Iterable<JsonNode> asNodeArray(JsonNode raw) {
        if (raw == null || raw.isNull()) {
            return List.of();
        }
        if (raw.isArray()) {
            return raw;
        }
        if (raw.has("items") && raw.get("items").isArray()) {
            return raw.get("items");
        }
        if (raw.has("content") && raw.get("content").isArray()) {
            return raw.get("content");
        }
        if (raw.has("data") && raw.get("data").isArray()) {
            return raw.get("data");
        }
        if (raw.has("data") && raw.get("data").has("items") && raw.get("data").get("items").isArray()) {
            return raw.get("data").get("items");
        }
        return List.of(raw);
    }

    private static String stringOr(JsonNode node, String field, String defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            String value = node.get(field).asText();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return defaultValue;
    }
}
