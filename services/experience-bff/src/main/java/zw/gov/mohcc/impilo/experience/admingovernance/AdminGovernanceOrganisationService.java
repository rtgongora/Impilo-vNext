package zw.gov.mohcc.impilo.experience.admingovernance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;

import java.util.*;

@Service
public class AdminGovernanceOrganisationService {

    private final SessionExperienceService sessionExperienceService;
    private final AdminGovernancePolicyService policyService;
    private final AdminGovernanceAuditHelper auditHelper;
    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final ObjectMapper objectMapper;

    public AdminGovernanceOrganisationService(SessionExperienceService sessionExperienceService,
                                              AdminGovernancePolicyService policyService,
                                              AdminGovernanceAuditHelper auditHelper,
                                              WorkforceGovernanceClient workforceGovernanceClient,
                                              ObjectMapper objectMapper) {
        this.sessionExperienceService = sessionExperienceService;
        this.policyService = policyService;
        this.auditHelper = auditHelper;
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.objectMapper = objectMapper;
    }

    public AdminGovernanceDtos.LookupEnvelope listOrganisations(String tenantId, String actorId, String providerId, boolean hasFacility) {
        JsonNode data = workforceGovernanceClient.listOrganisations();
        if (data == null || data.isNull()) {
            return unavailable("Organisation registry is not currently available.");
        }
        return live(Map.of("items", objectMapper.convertValue(data, List.class)));
    }

    public AdminGovernanceDtos.ActionResponse createOrganisation(String tenantId, String actorId, String providerId, boolean hasFacility,
                                                                 AdminGovernanceDtos.OrganisationUpsertRequest request) {
        AdminGovernanceDtos.PolicyDecision decision = evaluate(actorId, providerId, hasFacility, "ORGANISATION_CREATE", request.organisationCode(), null);
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied(decision.warnings().isEmpty() ? "Organisation create blocked." : decision.warnings().get(0), decision);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organisationCode", request.organisationCode());
        body.put("name", request.tradingName() != null ? request.tradingName() : request.legalName());
        body.put("legalName", request.legalName());
        body.put("organisationType", request.organisationType());
        if (request.parentOrganisationId() != null) body.put("parentOrganisationId", request.parentOrganisationId());
        body.put("metadataJson", metadataJson(request));
        JsonNode created = workforceGovernanceClient.postJson("/v1/internal/governance/organisations", body);
        if (created == null || created.isNull()) {
            return AdminGovernanceResponses.pendingBackend("Workforce governance unavailable — organisation not persisted.", decision);
        }
        var audit = auditHelper.emit("organisation.created", actorId, created.path("id").asText(), body);
        return AdminGovernanceResponses.of(
                "completed",
                null,
                created.path("id").asText(),
                null,
                "Organisation registered",
                "Organisation record created in workforce governance registry.",
                List.of("View organisation", "Start verification"),
                null,
                decision,
                audit,
                "live",
                Map.of("organisation", objectMapper.convertValue(created, Map.class))
        );
    }

    public AdminGovernanceDtos.LookupEnvelope getOrganisation(String orgId) {
        JsonNode summary = workforceGovernanceClient.organisationSummary(orgId);
        JsonNode org = workforceGovernanceClient.getJson("/v1/internal/governance/organisations/" + orgId);
        if ((org == null || org.isNull()) && (summary == null || summary.isNull())) {
            return unavailable("Organisation record unavailable.");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (org != null && !org.isNull()) data.put("organisation", objectMapper.convertValue(org, Map.class));
        if (summary != null && !summary.isNull()) data.put("summary", objectMapper.convertValue(summary, Map.class));
        return live(data);
    }

    public AdminGovernanceDtos.ActionResponse organisationLifecycleAction(String tenantId, String actorId, String providerId, boolean hasFacility,
                                                                          String orgId, String action, String sourcePage) {
        String policyAction = switch (action) {
            case "verify" -> "ORGANISATION_VERIFY";
            case "suspend" -> "ORGANISATION_SUSPEND";
            case "offboard" -> "ORGANISATION_OFFBOARD";
            default -> "ORGANISATION_" + action.toUpperCase(Locale.ROOT);
        };
        AdminGovernanceDtos.PolicyDecision decision = evaluate(actorId, providerId, hasFacility, policyAction, orgId, null);
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied(decision.warnings().get(0), decision);
        }
        String status = switch (action) {
            case "verify" -> "ACTIVE";
            case "suspend" -> "SUSPENDED";
            case "offboard" -> "CLOSED";
            default -> "INACTIVE";
        };
        JsonNode updated = workforceGovernanceClient.patchJson(
                "/v1/internal/governance/organisations/" + orgId + "/status",
                Map.of("status", status));
        if (updated == null || updated.isNull()) {
            return AdminGovernanceResponses.pendingBackend("Workforce governance unavailable — lifecycle change not persisted.", decision);
        }
        var audit = auditHelper.emit("organisation." + action, actorId, orgId, Map.of("status", status, "sourcePage", sourcePage));
        return AdminGovernanceResponses.of(
                "completed",
                null,
                orgId,
                null,
                "Organisation " + action,
                "Organisation lifecycle updated to " + status + ".",
                List.of("View organisation", "Audit review"),
                null,
                decision,
                audit,
                "live",
                Map.of("organisation", objectMapper.convertValue(updated, Map.class))
        );
    }

    public AdminGovernanceDtos.LookupEnvelope listOrganisationUsers(String orgId) {
        JsonNode users = workforceGovernanceClient.getJson("/v1/internal/governance/organisations/" + orgId + "/users");
        if (users == null || users.isNull()) {
            return unavailable("Organisation users unavailable.");
        }
        return live(Map.of("items", objectMapper.convertValue(users, List.class)));
    }

    public AdminGovernanceDtos.ActionResponse organisationUserAction(String actorId, String providerId, boolean hasFacility,
                                                                     String orgId, String userId, String action,
                                                                     AdminGovernanceDtos.OrganisationUserRequest request, String sourcePage) {
        String policyAction = switch (action) {
            case "add" -> "ORGANISATION_USER_CREATE";
            case "suspend" -> "ORGANISATION_USER_SUSPEND";
            case "offboard" -> "ORGANISATION_USER_OFFBOARD";
            default -> "ORGANISATION_USER_" + action.toUpperCase(Locale.ROOT);
        };
        AdminGovernanceDtos.PolicyDecision decision = evaluate(actorId, providerId, hasFacility, policyAction, orgId, userId);
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied(decision.warnings().get(0), decision);
        }
        JsonNode result;
        if ("add".equals(action)) {
            result = workforceGovernanceClient.postJson("/v1/internal/governance/organisations/" + orgId + "/users",
                    Map.of("userId", request.userId(), "roleTemplate", request.roleTemplate(), "status", "ACTIVE"));
        } else if ("suspend".equals(action)) {
            result = workforceGovernanceClient.postJson("/v1/internal/governance/organisations/" + orgId + "/users/" + userId + "/suspend", Map.of());
        } else {
            result = workforceGovernanceClient.postJson("/v1/internal/governance/organisations/" + orgId + "/users/" + userId + "/offboard", Map.of());
        }
        if (result == null || result.isNull()) {
            return AdminGovernanceResponses.pendingBackend("Workforce governance unavailable — organisation user change not persisted.", decision);
        }
        var audit = auditHelper.emit("organisation.user." + action, actorId, userId, Map.of("organisationId", orgId, "sourcePage", sourcePage));
        return AdminGovernanceResponses.of(
                "completed",
                null,
                userId,
                null,
                "Organisation user " + action,
                "Organisation membership updated.",
                List.of("View organisation users"),
                null,
                decision,
                audit,
                "live",
                Map.of("member", objectMapper.convertValue(result, Map.class))
        );
    }

    private AdminGovernanceDtos.PolicyDecision evaluate(String actorId, String providerId, boolean hasFacility,
                                                        String action, String orgId, String subjectId) {
        AdminGovernanceDtos.PrecheckRequest precheck = new AdminGovernanceDtos.PrecheckRequest(
                action,
                "/work/administration-governance/organisations",
                orgId,
                subjectId,
                null,
                null,
                null,
                null,
                null,
                null,
                "MEDIUM",
                Map.of()
        );
        return policyService.evaluate(
                sessionExperienceService.buildExperienceContract(actorId, null, providerId, hasFacility),
                precheck);
    }

    private String metadataJson(AdminGovernanceDtos.OrganisationUpsertRequest request) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("tradingName", request.tradingName());
        meta.put("country", request.country());
        meta.put("registrationNumber", request.registrationNumber());
        meta.put("regulatorRegistrationNumber", request.regulatorRegistrationNumber());
        meta.put("ownershipType", request.ownershipType());
        meta.put("publicPrivateClassification", request.publicPrivateClassification());
        meta.put("operatingJurisdictions", request.operatingJurisdictions());
        meta.put("sourceRegistry", "workforce_governance");
        if (request.provenanceMetadata() != null) meta.putAll(request.provenanceMetadata());
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static AdminGovernanceDtos.LookupEnvelope unavailable(String message) {
        return new AdminGovernanceDtos.LookupEnvelope("pending_backend", message, Map.of());
    }

    private static AdminGovernanceDtos.LookupEnvelope live(Map<String, Object> data) {
        return new AdminGovernanceDtos.LookupEnvelope("live", null, data);
    }
}
