package zw.gov.mohcc.impilo.experience.admingovernance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;

import java.util.*;

@Service
public class AdminGovernanceAuthorisedRepresentativeService {

    private final SessionExperienceService sessionExperienceService;
    private final AdminGovernancePolicyService policyService;
    private final AdminGovernanceAuditHelper auditHelper;
    private final GovernanceInvitationService governanceInvitationService;
    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final ObjectMapper objectMapper;

    public AdminGovernanceAuthorisedRepresentativeService(SessionExperienceService sessionExperienceService,
                                                          AdminGovernancePolicyService policyService,
                                                          AdminGovernanceAuditHelper auditHelper,
                                                          GovernanceInvitationService governanceInvitationService,
                                                          WorkforceGovernanceClient workforceGovernanceClient,
                                                          ObjectMapper objectMapper) {
        this.sessionExperienceService = sessionExperienceService;
        this.policyService = policyService;
        this.auditHelper = auditHelper;
        this.governanceInvitationService = governanceInvitationService;
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.objectMapper = objectMapper;
    }

    public AdminGovernanceDtos.LookupEnvelope list(String orgId) {
        JsonNode items = workforceGovernanceClient.getJson("/v1/internal/governance/organisations/" + orgId + "/authorised-representatives");
        if (items == null) return new AdminGovernanceDtos.LookupEnvelope("pending_backend", "Authorised representatives unavailable.", Map.of());
        return new AdminGovernanceDtos.LookupEnvelope("live", null, Map.of("items", objectMapper.convertValue(items, List.class)));
    }

    public AdminGovernanceDtos.ActionResponse invite(String actorId, String providerId, boolean hasFacility, String orgId, Map<String, Object> body) {
        AdminGovernanceDtos.PolicyDecision decision = policyService.evaluate(
                sessionExperienceService.buildExperienceContract(actorId, null, providerId, hasFacility),
                new AdminGovernanceDtos.PrecheckRequest(
                        "AUTHORISED_REPRESENTATIVE_INVITE",
                        "/work/administration-governance/organisations/" + orgId + "/users",
                        orgId, str(body.get("userId")), str(body.get("roleTemplateId")), null, null, null, null, null, "MEDIUM", body));
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied(decision.warnings().get(0), decision);
        }
        if (Objects.equals(actorId, str(body.get("userId")))) {
            return AdminGovernanceResponses.denied("Authorised representatives cannot self-escalate privileges.", decision);
        }

        String email = resolveEmail(body);
        if (email.isBlank()) {
            return AdminGovernanceResponses.denied("Official email is required for invitations.", decision);
        }

        body.put("approvedByUserId", actorId);
        JsonNode rep = workforceGovernanceClient.postJson("/v1/internal/governance/organisations/" + orgId + "/authorised-representatives", body);
        if (rep == null) return AdminGovernanceResponses.pendingBackend("Workforce governance unavailable.", decision);

        String roleTemplateId = strOrDefault(body.get("roleTemplateId"), "organisation_authorised_representative");
        String displayName = strOrDefault(body.get("fullName"), email);
        GovernanceInvitationService.InvitationDeliveryResult delivery = governanceInvitationService.deliverOrganisationInvitation(
                orgId,
                email,
                displayName,
                roleTemplateId,
                "authorised_representative");

        if (!delivery.activated()) {
            var audit = auditHelper.emit("authorised_representative.invitation_blocked", actorId, rep.path("id").asText(), Map.of(
                    "status", delivery.status(),
                    "auditStatus", delivery.auditStatus()));
            return AdminGovernanceResponses.of(
                    "blocked".equals(delivery.status()) ? "denied" : "pending_backend",
                    rep.path("id").asText(),
                    str(body.get("userId")),
                    null,
                    "Invitation not delivered",
                    delivery.friendlyMessage(),
                    List.of("Retry invitation", "Contact Support"),
                    delivery.friendlyMessage(),
                    decision,
                    audit,
                    delivery.auditStatus(),
                    Map.of("representative", rep, "invitationStatus", delivery.status()));
        }

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("status", "INVITATION_SENT");
        patch.put("invitationId", delivery.invitationId());
        patch.put("keycloakUserId", delivery.keycloakUserId());
        patch.put("invitationAuditStatus", delivery.auditStatus());
        JsonNode updated = workforceGovernanceClient.patchJson(
                "/v1/internal/governance/organisations/" + orgId + "/authorised-representatives/" + rep.path("id").asText(),
                patch);

        var audit = auditHelper.emit("authorised_representative.invited", actorId, rep.path("id").asText(), Map.of(
                "invitationId", delivery.invitationId(),
                "keycloakUserId", delivery.keycloakUserId(),
                "notificationAuditStatus", delivery.auditStatus()));
        return AdminGovernanceResponses.of(
                "completed",
                rep.path("id").asText(),
                str(body.get("userId")),
                null,
                "Authorised representative invited",
                delivery.friendlyMessage(),
                List.of("View representatives"),
                null,
                decision,
                audit,
                delivery.auditStatus(),
                Map.of("representative", updated != null ? updated : rep, "invitationId", delivery.invitationId()));
    }

    public AdminGovernanceDtos.ActionResponse lifecycle(String actorId, String providerId, boolean hasFacility, String orgId, String repId, String action) {
        String path = "/v1/internal/governance/organisations/" + orgId + "/authorised-representatives/" + repId + "/" + action;
        JsonNode rep = workforceGovernanceClient.postJson(path, Map.of());
        if (rep == null) return AdminGovernanceResponses.pendingBackend("Representative update unavailable.", null);
        var audit = auditHelper.emit("authorised_representative." + action, actorId, repId, Map.of());
        return AdminGovernanceResponses.of("completed", repId, null, null, "Representative " + action,
                "Representative status updated.", List.of("View representatives"), null, null, audit, "live", Map.of("representative", rep));
    }

    private static String resolveEmail(Map<String, Object> body) {
        String official = str(body.get("officialEmail"));
        if (!official.isBlank()) return official;
        String userId = str(body.get("userId"));
        return userId.contains("@") ? userId : "";
    }

    private static String str(Object value) { return value == null ? "" : value.toString(); }

    private static String strOrDefault(Object value, String defaultValue) {
        String s = str(value);
        return s.isBlank() ? defaultValue : s;
    }
}
