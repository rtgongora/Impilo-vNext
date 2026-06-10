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
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Object item : objectMapper.convertValue(items, List.class)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rep = objectMapper.convertValue(item, Map.class);
            rep.put("invitation", InvitationLifecycle.toMap(InvitationLifecycle.fromRepresentative(rep, objectMapper)));
            enriched.add(rep);
        }
        return new AdminGovernanceDtos.LookupEnvelope("live", null, Map.of("items", enriched));
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
        patch.put("invitationStatus", "sent");
        patch.put("expiresAt", delivery.expiresAt());
        patch.put("invitationAuditTrail", List.of(Map.of(
                "eventType", "invitation.sent",
                "invitationId", delivery.invitationId(),
                "expiresAt", delivery.expiresAt(),
                "occurredAt", java.time.OffsetDateTime.now().toString()
        )));
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
                Map.of(
                        "representative", updated != null ? updated : rep,
                        "invitationId", delivery.invitationId(),
                        "invitation", InvitationLifecycle.toMap(InvitationLifecycle.fromRepresentative(
                                objectMapper.convertValue(updated != null ? updated : rep, Map.class), objectMapper))));
    }

    public AdminGovernanceDtos.LookupEnvelope invitationAuditTrail(String orgId, String repId) {
        JsonNode items = workforceGovernanceClient.getJson("/v1/internal/governance/organisations/" + orgId + "/authorised-representatives");
        if (items == null) {
            return new AdminGovernanceDtos.LookupEnvelope("pending_backend", "Invitation audit trail unavailable.", Map.of());
        }
        for (JsonNode item : items) {
            if (repId.equals(item.path("id").asText())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rep = objectMapper.convertValue(item, Map.class);
                InvitationLifecycle.InvitationView view = InvitationLifecycle.fromRepresentative(rep, objectMapper);
                return new AdminGovernanceDtos.LookupEnvelope("live", null, Map.of(
                        "invitation", InvitationLifecycle.toMap(view),
                        "auditTrail", view.auditTrail()));
            }
        }
        return new AdminGovernanceDtos.LookupEnvelope("pending_backend", "Representative not found.", Map.of());
    }

    public AdminGovernanceDtos.ActionResponse resendInvitation(String actorId, String providerId, boolean hasFacility, String orgId, String repId) {
        Map<String, Object> rep = findRepresentative(orgId, repId);
        if (rep == null) return AdminGovernanceResponses.pendingBackend("Representative not found.", null);
        InvitationLifecycle.InvitationView view = InvitationLifecycle.fromRepresentative(rep, objectMapper);
        if (!view.canResend()) {
            return AdminGovernanceResponses.denied("Resend is not allowed for the current invitation state.", null);
        }
        AdminGovernanceDtos.PolicyDecision decision = policyService.evaluate(
                sessionExperienceService.buildExperienceContract(actorId, null, providerId, hasFacility),
                new AdminGovernanceDtos.PrecheckRequest(
                        "AUTHORISED_REPRESENTATIVE_INVITE",
                        "/work/administration-governance/organisations/" + orgId + "/users",
                        orgId, str(rep.get("userId")), str(rep.get("roleTemplateId")), null, null, null, null, null, "MEDIUM",
                        Map.of("intent", "RESEND_INVITATION", "representativeId", repId)));
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied(decision.warnings().get(0), decision);
        }
        String email = resolveEmail(rep);
        GovernanceInvitationService.InvitationDeliveryResult delivery = governanceInvitationService.deliverOrganisationInvitation(
                orgId, email, strOrDefault(rep.get("fullName"), email), strOrDefault(rep.get("roleTemplateId"), "organisation_authorised_representative"), "authorised_representative_resend");
        if (!delivery.activated()) {
            return AdminGovernanceResponses.of("denied", repId, str(rep.get("userId")), null, "Invitation not resent", delivery.friendlyMessage(),
                    List.of("Contact Support"), delivery.friendlyMessage(), decision, null, delivery.auditStatus(), Map.of("invitationStatus", delivery.status()));
        }
        Map<String, Object> patch = invitationPatch(delivery, "sent", appendAudit(view.auditTrail(), "invitation.resent", delivery));
        JsonNode updated = workforceGovernanceClient.patchJson("/v1/internal/governance/organisations/" + orgId + "/authorised-representatives/" + repId, patch);
        var audit = auditHelper.emit("authorised_representative.invitation_resent", actorId, repId, Map.of("invitationId", delivery.invitationId()));
        return AdminGovernanceResponses.of("completed", repId, str(rep.get("userId")), null, "Invitation resent", delivery.friendlyMessage(),
                List.of("View representatives"), null, decision, audit, delivery.auditStatus(),
                Map.of("representative", updated != null ? updated : rep, "invitation", InvitationLifecycle.toMap(InvitationLifecycle.fromRepresentative(
                        objectMapper.convertValue(updated != null ? updated : rep, Map.class), objectMapper))));
    }

    public AdminGovernanceDtos.ActionResponse revokeInvitation(String actorId, String providerId, boolean hasFacility, String orgId, String repId) {
        Map<String, Object> rep = findRepresentative(orgId, repId);
        if (rep == null) return AdminGovernanceResponses.pendingBackend("Representative not found.", null);
        InvitationLifecycle.InvitationView view = InvitationLifecycle.fromRepresentative(rep, objectMapper);
        if (!view.canRevoke()) {
            return AdminGovernanceResponses.denied("Revoke is not allowed for the current invitation state.", null);
        }
        AdminGovernanceDtos.PolicyDecision decision = policyService.evaluate(
                sessionExperienceService.buildExperienceContract(actorId, null, providerId, hasFacility),
                new AdminGovernanceDtos.PrecheckRequest(
                        "AUTHORISED_REPRESENTATIVE_INVITE",
                        "/work/administration-governance/organisations/" + orgId + "/users",
                        orgId, str(rep.get("userId")), str(rep.get("roleTemplateId")), null, null, null, null, null, "MEDIUM",
                        Map.of("intent", "REVOKE_INVITATION", "representativeId", repId)));
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied(decision.warnings().get(0), decision);
        }
        GovernanceInvitationService.InvitationRevocationResult revocation = governanceInvitationService.revokeInvitation(view.keycloakUserId());
        if (!revocation.revoked()) {
            return AdminGovernanceResponses.of("denied", repId, str(rep.get("userId")), null, "Invitation not revoked", revocation.friendlyMessage(),
                    List.of("Contact Support"), revocation.friendlyMessage(), decision, null, revocation.auditStatus(), Map.of());
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("invitationStatus", "revoked");
        patch.put("status", "REVOKED");
        patch.put("invitationAuditTrail", appendAudit(view.auditTrail(), "invitation.revoked", null));
        JsonNode updated = workforceGovernanceClient.patchJson("/v1/internal/governance/organisations/" + orgId + "/authorised-representatives/" + repId, patch);
        var audit = auditHelper.emit("authorised_representative.invitation_revoked", actorId, repId, Map.of());
        return AdminGovernanceResponses.of("completed", repId, str(rep.get("userId")), null, "Invitation revoked",
                revocation.friendlyMessage(), List.of("View representatives"), null, decision, audit, revocation.auditStatus(),
                Map.of("representative", updated != null ? updated : rep, "invitation", InvitationLifecycle.toMap(InvitationLifecycle.fromRepresentative(
                        objectMapper.convertValue(updated != null ? updated : rep, Map.class), objectMapper))));
    }

    public AdminGovernanceDtos.ActionResponse lifecycle(String actorId, String providerId, boolean hasFacility, String orgId, String repId, String action) {
        String path = "/v1/internal/governance/organisations/" + orgId + "/authorised-representatives/" + repId + "/" + action;
        JsonNode rep = workforceGovernanceClient.postJson(path, Map.of());
        if (rep == null) return AdminGovernanceResponses.pendingBackend("Representative update unavailable.", null);
        var audit = auditHelper.emit("authorised_representative." + action, actorId, repId, Map.of());
        return AdminGovernanceResponses.of("completed", repId, null, null, "Representative " + action,
                "Representative status updated.", List.of("View representatives"), null, null, audit, "live", Map.of("representative", rep));
    }

    private Map<String, Object> findRepresentative(String orgId, String repId) {
        JsonNode items = workforceGovernanceClient.getJson("/v1/internal/governance/organisations/" + orgId + "/authorised-representatives");
        if (items == null || !items.isArray()) return null;
        for (JsonNode item : items) {
            if (repId.equals(item.path("id").asText())) {
                return objectMapper.convertValue(item, Map.class);
            }
        }
        return null;
    }

    private Map<String, Object> invitationPatch(
            GovernanceInvitationService.InvitationDeliveryResult delivery,
            String invitationStatus,
            List<Map<String, Object>> auditTrail) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("status", "INVITATION_SENT");
        patch.put("invitationId", delivery.invitationId());
        patch.put("keycloakUserId", delivery.keycloakUserId());
        patch.put("invitationAuditStatus", delivery.auditStatus());
        patch.put("invitationStatus", invitationStatus);
        patch.put("expiresAt", delivery.expiresAt());
        patch.put("invitationAuditTrail", auditTrail);
        return patch;
    }

    private List<Map<String, Object>> appendAudit(
            List<Map<String, Object>> existing,
            String eventType,
            GovernanceInvitationService.InvitationDeliveryResult delivery) {
        List<Map<String, Object>> trail = new ArrayList<>(existing != null ? existing : List.of());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", eventType);
        event.put("occurredAt", java.time.OffsetDateTime.now().toString());
        if (delivery != null) {
            event.put("invitationId", delivery.invitationId());
            event.put("expiresAt", delivery.expiresAt());
        }
        trail.add(event);
        return trail;
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
