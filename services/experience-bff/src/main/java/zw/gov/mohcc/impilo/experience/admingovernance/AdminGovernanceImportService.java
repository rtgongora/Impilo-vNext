package zw.gov.mohcc.impilo.experience.admingovernance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;

import java.util.*;

@Service
public class AdminGovernanceImportService {

    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final AdminGovernancePolicyService policyService;
    private final AdminGovernanceAuditHelper auditHelper;
    private final GovernanceInvitationService governanceInvitationService;
    private final SessionExperienceService sessionExperienceService;
    private final ObjectMapper objectMapper;

    public AdminGovernanceImportService(WorkforceGovernanceClient workforceGovernanceClient,
                                        AdminGovernancePolicyService policyService,
                                        AdminGovernanceAuditHelper auditHelper,
                                        GovernanceInvitationService governanceInvitationService,
                                        SessionExperienceService sessionExperienceService,
                                        ObjectMapper objectMapper) {
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.policyService = policyService;
        this.auditHelper = auditHelper;
        this.governanceInvitationService = governanceInvitationService;
        this.sessionExperienceService = sessionExperienceService;
        this.objectMapper = objectMapper;
    }

    public AdminGovernanceDtos.LookupEnvelope upload(String actorId, String providerId, boolean hasFacility,
                                                     String organisationId, String importType, String fileName, String csvContent) {
        AdminGovernanceDtos.PolicyDecision decision = policyService.evaluate(
                sessionExperienceService.buildExperienceContract(actorId, null, providerId, hasFacility),
                new AdminGovernanceDtos.PrecheckRequest(
                        importAction(importType),
                        "/work/administration-governance/organisations/" + organisationId + "/data-uploads",
                        organisationId, null, null, importType, null, null, null, null, "MEDIUM",
                        Map.of("importType", importType)));
        if (!decision.allowed()) {
            return unavailable(decision.warnings().isEmpty() ? "Import blocked by policy." : decision.warnings().get(0));
        }
        Map<String, Object> body = Map.of(
                "organisationId", organisationId,
                "uploadedByUserId", actorId,
                "importType", importType,
                "sourceFileName", fileName,
                "csvContent", csvContent
        );
        JsonNode batch = workforceGovernanceClient.postJson("/v1/internal/governance/imports", body);
        if (batch == null || batch.isNull()) {
            return fallbackUpload(organisationId, importType, fileName, csvContent, actorId);
        }
        var audit = auditHelper.emit("import.batch_uploaded", actorId, batch.path("id").asText(), body);
        return live(Map.of(
                "batch", objectMapper.convertValue(batch, Map.class),
                "sourceOfRecordStatus", "workforce_governance",
                "auditStatus", audit.auditStatus()
        ));
    }

    public AdminGovernanceDtos.LookupEnvelope list(String organisationId) {
        JsonNode items = workforceGovernanceClient.getJson("/v1/internal/governance/imports?organisationId=" + organisationId);
        if (items == null) return unavailable("Import history unavailable.");
        return live(Map.of("items", objectMapper.convertValue(items, List.class)));
    }

    public AdminGovernanceDtos.ActionResponse approve(String actorId, String importBatchId) {
        JsonNode batch = workforceGovernanceClient.postJson("/v1/internal/governance/imports/" + importBatchId + "/approve", Map.of("actorId", actorId));
        if (batch == null) return AdminGovernanceResponses.pendingBackend("Import approval could not be persisted.", null);
        var audit = auditHelper.emit("import.batch_approved", actorId, importBatchId, Map.of());
        return AdminGovernanceResponses.of("completed", importBatchId, null, null, "Import approved",
                "Staged rows may proceed to invitation — users are not activated automatically.",
                List.of("Send invitations"), null, null, audit, "live", Map.of("batch", batch));
    }

    public AdminGovernanceDtos.ActionResponse sendInvitations(String actorId, String importBatchId) {
        JsonNode batchMeta = workforceGovernanceClient.getJson("/v1/internal/governance/imports/" + importBatchId);
        if (batchMeta == null) {
            return AdminGovernanceResponses.pendingBackend("Import batch unavailable.", null);
        }
        String organisationId = batchMeta.path("organisationId").asText("");
        String importType = batchMeta.path("importType").asText("organisation_users");

        JsonNode rowsNode = workforceGovernanceClient.getJson("/v1/internal/governance/imports/" + importBatchId + "/rows");
        if (rowsNode == null || !rowsNode.isArray()) {
            return AdminGovernanceResponses.pendingBackend("Import rows unavailable.", null);
        }

        List<Map<String, Object>> deliveries = new ArrayList<>();
        List<String> blockedMessages = new ArrayList<>();
        for (JsonNode row : rowsNode) {
            String outcome = row.path("outcome").asText("");
            if (!"ready_to_invite".equals(outcome) && !"requires_approval".equals(outcome)) {
                continue;
            }
            Map<String, String> payload = parseRowPayload(row.path("normalizedPayloadJson").asText(null));
            String email = payload.getOrDefault("email", payload.getOrDefault("official_email", ""));
            String displayName = payload.getOrDefault("full_name", email);
            GovernanceInvitationService.InvitationDeliveryResult delivery = governanceInvitationService.deliverImportRowInvitation(
                    organisationId,
                    importBatchId,
                    email,
                    displayName,
                    importType);
            if (!delivery.activated()) {
                blockedMessages.add(email.isBlank() ? row.path("id").asText() + ": " + delivery.friendlyMessage() : email + ": " + delivery.friendlyMessage());
                continue;
            }
            deliveries.add(Map.of(
                    "rowId", row.path("id").asText(),
                    "invitationId", delivery.invitationId(),
                    "keycloakUserId", delivery.keycloakUserId() != null ? delivery.keycloakUserId() : ""
            ));
        }

        if (deliveries.isEmpty()) {
            var audit = auditHelper.emit("import.invitations_blocked", actorId, importBatchId, Map.of("blocked", blockedMessages));
            return AdminGovernanceResponses.of(
                    "denied",
                    importBatchId,
                    null,
                    null,
                    "No invitations delivered",
                    blockedMessages.isEmpty()
                            ? "No rows were ready for invitation."
                            : blockedMessages.get(0),
                    List.of("Review import exceptions", "Retry invitations"),
                    blockedMessages.isEmpty() ? "No eligible rows" : blockedMessages.get(0),
                    null,
                    audit,
                    "failed_non_blocking",
                    Map.of("blocked", blockedMessages));
        }

        JsonNode batch = workforceGovernanceClient.postJson(
                "/v1/internal/governance/imports/" + importBatchId + "/record-invitations",
                Map.of("deliveries", deliveries));
        if (batch == null) {
            return AdminGovernanceResponses.pendingBackend(
                    "Invitations were created in Keycloak but workforce governance could not record them.",
                    null);
        }

        var audit = auditHelper.emit("import.invitations_sent", actorId, importBatchId, Map.of(
                "deliveredCount", deliveries.size(),
                "blockedCount", blockedMessages.size()));
        String message = deliveries.size() + " invitation(s) sent. Activation requires identity confirmation and OPA precheck.";
        if (!blockedMessages.isEmpty()) {
            message += " " + blockedMessages.size() + " row(s) could not be invited.";
        }
        return AdminGovernanceResponses.of(
                "completed",
                importBatchId,
                null,
                null,
                "Invitations sent",
                message,
                List.of("View import history"),
                null,
                null,
                audit,
                audit.auditStatus(),
                Map.of("batch", batch, "deliveredCount", deliveries.size(), "blocked", blockedMessages));
    }

    public AdminGovernanceDtos.LookupEnvelope template(String importType) {
        JsonNode template = workforceGovernanceClient.getJson("/v1/internal/governance/imports/templates/" + importType);
        if (template == null) {
            return live(Map.of("importType", importType, "requiredColumns", List.of("email", "full_name", "role_template"), "supportedFormats", List.of("csv", "json")));
        }
        return live(objectMapper.convertValue(template, Map.class));
    }

    private AdminGovernanceDtos.LookupEnvelope fallbackUpload(String organisationId, String importType, String fileName, String csvContent, String actorId) {
        List<Map<String, String>> rows = CsvImportParser.parseCsv(csvContent);
        String batchId = UUID.randomUUID().toString();
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("id", batchId);
        batch.put("organisationId", organisationId);
        batch.put("importType", importType);
        batch.put("status", "validated");
        batch.put("rowCount", rows.size());
        batch.put("sourceOfRecordStatus", "fallback_queue");
        batch.put("reconciliationRequired", true);
        var audit = auditHelper.emit("import.batch_uploaded", actorId, batchId, Map.of("fallback", true));
        return live(Map.of("batch", batch, "sourceOfRecordStatus", "fallback_queue", "reconciliationRequired", true, "auditStatus", audit.auditStatus()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseRowPayload(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static AdminGovernanceDtos.LookupEnvelope unavailable(String message) {
        return new AdminGovernanceDtos.LookupEnvelope("pending_backend", message, Map.of());
    }

    private static AdminGovernanceDtos.LookupEnvelope live(Map<String, Object> data) {
        return new AdminGovernanceDtos.LookupEnvelope("live", null, data);
    }

    private static String importAction(String importType) {
        return switch (importType) {
            case "organisation_users" -> "BULK_IMPORT_ORGANISATION_USERS";
            case "hsc_employment_records" -> "BULK_IMPORT_HSC_EMPLOYMENT_RECORDS";
            case "council_professional_register" -> "BULK_IMPORT_COUNCIL_PROFESSIONAL_REGISTER";
            case "facility_staff_list" -> "BULK_IMPORT_FACILITY_STAFF_LIST";
            default -> "BULK_IMPORT_ORGANISATION_USERS";
        };
    }
}
