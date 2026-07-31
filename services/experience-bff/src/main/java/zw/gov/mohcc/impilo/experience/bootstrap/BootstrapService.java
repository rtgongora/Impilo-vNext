package zw.gov.mohcc.impilo.experience.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.admingovernance.GovernanceInvitationService;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.auth.session.WebAuthSessionStore;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BootstrapService {

    private static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    private final BootstrapProperties properties;
    private final BootstrapStateRepository stateRepository;
    private final BootstrapTokenService tokenService;
    private final BootstrapPolicyService policyService;
    private final BootstrapAuditService auditService;
    private final GovernanceInvitationService governanceInvitationService;
    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final BootstrapActivationVerifier activationVerifier;

    public BootstrapService(BootstrapProperties properties,
                            BootstrapStateRepository stateRepository,
                            BootstrapTokenService tokenService,
                            BootstrapPolicyService policyService,
                            BootstrapAuditService auditService,
                            GovernanceInvitationService governanceInvitationService,
                            WorkforceGovernanceClient workforceGovernanceClient,
                            BootstrapActivationVerifier activationVerifier) {
        this.properties = properties;
        this.stateRepository = stateRepository;
        this.tokenService = tokenService;
        this.policyService = policyService;
        this.auditService = auditService;
        this.governanceInvitationService = governanceInvitationService;
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.activationVerifier = activationVerifier;
    }

    public BootstrapDtos.BootstrapStatusResponse status(String tenantId) {
        String tid = tenantId != null ? tenantId : DEFAULT_TENANT;
        BootstrapDtos.BootstrapStateRecord state = stateRepository.getOrInit(tid);
        boolean activeNationalAdminExists = activeNationalAdminExists(state);
        auditService.emit("bootstrap.status_checked", null, tid, Map.of("bootstrapOpen", state.bootstrapOpen()));

        boolean bootstrapRequired = !activeNationalAdminExists || state.recoveryMode();
        boolean bootstrapOpen = state.bootstrapOpen() && (!state.bootstrapClosed() || state.recoveryMode());
        String message = bootstrapRequired
                ? "Bootstrap Mode is required to create the first national platform administrator."
                : "Bootstrap Mode is closed. Routine operations use governed administration workflows.";

        return new BootstrapDtos.BootstrapStatusResponse(
                bootstrapRequired,
                bootstrapOpen,
                state.bootstrapClosedAt(),
                activeNationalAdminExists,
                state.recoveryMode(),
                properties.getAllowedMethods(),
                state.bootstrapAccountId(),
                message
        );
    }

    public BootstrapDtos.ValidateTokenResponse validateToken(String tenantId, BootstrapDtos.ValidateTokenRequest request) {
        String tid = tenantId != null ? tenantId : DEFAULT_TENANT;
        BootstrapDtos.BootstrapStateRecord state = stateRepository.getOrInit(tid);
        BootstrapDtos.BootstrapPolicyDecision decision = policyService.evaluateStatus(state, activeNationalAdminExists(state), properties);
        if (!decision.allowed()) {
            var audit = auditService.emit("bootstrap.denied", null, tid, Map.of("reason", decision.blockedReasons()));
            return new BootstrapDtos.ValidateTokenResponse("denied", "Bootstrap blocked", decision.blockedReasons().get(0), false, audit.auditStatus(), audit.auditEventId());
        }
        BootstrapTokenService.TokenValidationResult tokenResult = tokenService.validate(
                request.bootstrapMethod(), request.bootstrapToken(), request.authorisationReference());
        if (!tokenResult.valid()) {
            var audit = auditService.emit("bootstrap.denied", null, tid, Map.of("reason", tokenResult.message()));
            return new BootstrapDtos.ValidateTokenResponse("denied", "Token invalid", tokenResult.message(), false, audit.auditStatus(), audit.auditEventId());
        }
        if (stateRepository.isTokenUsed(tid, tokenResult.fingerprint())) {
            var audit = auditService.emit("bootstrap.denied", null, tid, Map.of("reason", "token_already_used"));
            return new BootstrapDtos.ValidateTokenResponse("denied", "Token already used", "Bootstrap token has already been used.", false, audit.auditStatus(), audit.auditEventId());
        }
        var audit = auditService.emit("bootstrap.token_validated", null, tid, Map.of("method", request.bootstrapMethod()));
        return new BootstrapDtos.ValidateTokenResponse("allowed", "Token valid", "Bootstrap token validated.", true, audit.auditStatus(), audit.auditEventId());
    }

    public BootstrapDtos.BootstrapFirstAdminResponse createFirstAdmin(String tenantId, BootstrapDtos.BootstrapFirstAdminRequest request) {
        String tid = tenantId != null ? tenantId : DEFAULT_TENANT;
        BootstrapDtos.BootstrapStateRecord state = stateRepository.getOrInit(tid);
        BootstrapDtos.BootstrapPolicyDecision decision = policyService.evaluateFirstAdmin(state, activeNationalAdminExists(state), properties, request);
        if (!decision.allowed()) {
            var audit = auditService.emit("bootstrap.denied", null, tid, Map.of("reason", decision.blockedReasons()));
            return deniedResponse(decision, audit, decision.blockedReasons().get(0));
        }

        BootstrapTokenService.TokenValidationResult tokenResult = tokenService.validate(
                request.bootstrapMethod(), request.bootstrapToken(), request.authorisationReference());
        if (!tokenResult.valid()) {
            var audit = auditService.emit("bootstrap.denied", null, tid, Map.of("reason", tokenResult.message()));
            return deniedResponse(decision, audit, tokenResult.message());
        }
        if (stateRepository.isTokenUsed(tid, tokenResult.fingerprint())) {
            var audit = auditService.emit("bootstrap.denied", null, tid, Map.of("reason", "token_already_used"));
            return deniedResponse(decision, audit, "Bootstrap token has already been used.");
        }

        Map<String, Object> orgBody = sovereignOrganisationBody(request);
        JsonNode organisation = workforceGovernanceClient.postJson("/v1/internal/governance/organisations", orgBody);
        String organisationId = organisation != null && organisation.has("id") ? organisation.get("id").asText() : null;

        String bootstrapAccountId = UUID.randomUUID().toString();
        String email = str(request.nominatedPerson().get("officialEmail"));
        Map<String, Object> accountBody = new LinkedHashMap<>();
        accountBody.put("bootstrapAccountId", bootstrapAccountId);
        accountBody.put("bootstrapMethod", request.bootstrapMethod());
        accountBody.put("nominatedEmail", email);
        accountBody.put("nominatedFullName", request.nominatedPerson().get("fullName"));
        accountBody.put("sovereignOrganisationId", organisationId);
        accountBody.put("approvalReference", request.approvalReference());
        accountBody.put("status", "PENDING");
        workforceGovernanceClient.postJson("/v1/internal/governance/bootstrap/accounts", accountBody);

        GovernanceInvitationService.InvitationDeliveryResult delivery =
                governanceInvitationService.deliverBootstrapActivation(
                        email, str(request.nominatedPerson().get("fullName")), properties.isRequireMfa());
        if (!delivery.activated()) {
            var audit = auditService.emit("bootstrap.denied", email, bootstrapAccountId, Map.of(
                    "reason", delivery.status(), "auditStatus", delivery.auditStatus()));
            return deniedResponse(
                    new BootstrapDtos.BootstrapPolicyDecision(false, UUID.randomUUID().toString(),
                            List.of(), List.of(delivery.friendlyMessage())),
                    audit, delivery.friendlyMessage());
        }

        stateRepository.markTokenUsed(tid, tokenResult.fingerprint());
        String roleExpiry = OffsetDateTime.now().plusHours(properties.getBootstrapRoleTtlHours()).toString();
        BootstrapDtos.BootstrapStateRecord updated = new BootstrapDtos.BootstrapStateRecord(
                state.id(),
                tid,
                true,
                false,
                state.bootstrapClosedAt(),
                state.recoveryMode(),
                state.recoveryOpenedAt(),
                state.recoveryClosedAt(),
                state.activeNationalAdminUserId(),
                bootstrapAccountId,
                request.bootstrapMethod(),
                roleExpiry,
                state.createdAt(),
                OffsetDateTime.now().toString(),
                Map.of(
                        "organisationId", organisationId,
                        "nominatedEmail", email,
                        "nominatedFullName", str(request.nominatedPerson().get("fullName")),
                        "keycloakUserId", delivery.keycloakUserId(),
                        "invitationId", delivery.invitationId(),
                        "invitationExpiresAt", delivery.expiresAt()
                )
        );
        stateRepository.save(tid, updated);
        var audit = auditService.emit("bootstrap.first_admin_created", email, bootstrapAccountId, accountBody);

        return new BootstrapDtos.BootstrapFirstAdminResponse(
                "pending",
                delivery.invitationId(),
                organisationId,
                bootstrapAccountId,
                "Pending bootstrap administrator created",
                "Use the time-limited Keycloak action to set a password, register an approved hardware key, and confirm recovery codes. Then authenticate at AAL3 to activate.",
                false,
                roleExpiry,
                audit.auditStatus(),
                audit.auditEventId(),
                decision
        );
    }

    public BootstrapDtos.BootstrapFirstAdminResponse activate(String tenantId,
            BootstrapDtos.BootstrapActivateRequest request,
            WebAuthSessionStore.SessionData authenticatedSession) {
        String tid = tenantId != null ? tenantId : DEFAULT_TENANT;
        BootstrapDtos.BootstrapStateRecord state = stateRepository.getOrInit(tid);
        if (state.bootstrapClosed()) {
            var audit = auditService.emit("bootstrap.denied", null, tid, Map.of("reason", "bootstrap_closed"));
            return deniedResponse(new BootstrapDtos.BootstrapPolicyDecision(false, UUID.randomUUID().toString(), List.of(), List.of("Bootstrap is closed.")), audit, "Bootstrap Mode is closed.");
        }
        if (request == null || request.bootstrapAccountId() == null
                || !request.bootstrapAccountId().equals(state.bootstrapAccountId())) {
            var audit = auditService.emit("bootstrap.denied", null, tid,
                    Map.of("reason", "bootstrap_account_mismatch"));
            return deniedResponse(new BootstrapDtos.BootstrapPolicyDecision(false,
                    UUID.randomUUID().toString(), List.of(), List.of("Bootstrap account mismatch.")),
                    audit, "Bootstrap account does not match the open ceremony.");
        }
        String keycloakUserId = str(state.metadata() != null
                ? state.metadata().get("keycloakUserId") : null);
        BootstrapActivationVerifier.Verification verification =
                activationVerifier.verify(keycloakUserId, authenticatedSession);
        if (!verification.allowed()) {
            var audit = auditService.emit("bootstrap.denied", keycloakUserId, tid,
                    Map.of("reason", verification.message()));
            return deniedResponse(
                    new BootstrapDtos.BootstrapPolicyDecision(false, UUID.randomUUID().toString(),
                            List.of(), List.of(verification.message())), audit, verification.message());
        }
        if (!governanceInvitationService.activateBootstrapRoles(keycloakUserId)) {
            var audit = auditService.emit("bootstrap.denied", keycloakUserId, tid,
                    Map.of("reason", "keycloak_role_assignment_failed"));
            return deniedResponse(new BootstrapDtos.BootstrapPolicyDecision(false,
                    UUID.randomUUID().toString(), List.of(),
                    List.of("Governed administrator roles could not be assigned.")), audit,
                    "Administrator activation is pending because Keycloak role assignment failed.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bootstrapAccountId", request.bootstrapAccountId());
        body.put("authenticationAssurance", "AAL3");
        body.put("credentialCount", verification.credentialCount());
        body.put("status", "ACTIVE");
        body.put("keycloakUserId", keycloakUserId);
        body.put("invitationId", str(state.metadata() != null ? state.metadata().get("invitationId") : null));
        JsonNode activated = workforceGovernanceClient.postJson(
                "/v1/internal/governance/bootstrap/accounts/" + request.bootstrapAccountId() + "/activate", body);
        String userId = activated != null && activated.has("keycloakUserId")
                ? activated.path("keycloakUserId").asText(keycloakUserId)
                : keycloakUserId;

        BootstrapDtos.BootstrapStateRecord closed = new BootstrapDtos.BootstrapStateRecord(
                state.id(),
                tid,
                false,
                true,
                OffsetDateTime.now().toString(),
                false,
                state.recoveryOpenedAt(),
                state.recoveryClosedAt(),
                userId,
                state.bootstrapAccountId(),
                state.bootstrapMethod(),
                state.roleExpiresAt(),
                state.createdAt(),
                OffsetDateTime.now().toString(),
                Map.of("convertedRoles", List.of(
                        "national_platform_administrator",
                        "national_trust_administrator",
                        "national_organisation_registry_administrator",
                        "national_role_template_administrator",
                        "national_access_governance_administrator"
                ))
        );
        stateRepository.save(tid, closed);
        workforceGovernanceClient.postJson("/v1/internal/governance/bootstrap/close", Map.of("tenantId", tid));
        var audit = auditService.emit("bootstrap.first_admin_activated", userId, request.bootstrapAccountId(), body);
        auditService.emit("bootstrap.closed", userId, tid, Map.of("closedAt", closed.bootstrapClosedAt()));

        return new BootstrapDtos.BootstrapFirstAdminResponse(
                "completed",
                request.bootstrapAccountId(),
                str(state.metadata() != null ? state.metadata().get("organisationId") : null),
                request.bootstrapAccountId(),
                "Bootstrap complete",
                "Bootstrap Mode is now closed. Use Administration & Governance for routine operations.",
                true,
                state.roleExpiresAt(),
                audit.auditStatus(),
                audit.auditEventId(),
                new BootstrapDtos.BootstrapPolicyDecision(true, UUID.randomUUID().toString(), List.of("Bootstrap role converted to governed national roles."), List.of())
        );
    }

    public BootstrapDtos.BootstrapFirstAdminResponse close(String tenantId) {
        String tid = tenantId != null ? tenantId : DEFAULT_TENANT;
        BootstrapDtos.BootstrapStateRecord state = stateRepository.getOrInit(tid);
        BootstrapDtos.BootstrapStateRecord closed = new BootstrapDtos.BootstrapStateRecord(
                state.id(), tid, false, true, OffsetDateTime.now().toString(), false,
                state.recoveryOpenedAt(), state.recoveryClosedAt(), state.activeNationalAdminUserId(),
                state.bootstrapAccountId(), state.bootstrapMethod(), state.roleExpiresAt(),
                state.createdAt(), OffsetDateTime.now().toString(), state.metadata()
        );
        stateRepository.save(tid, closed);
        workforceGovernanceClient.postJson("/v1/internal/governance/bootstrap/close", Map.of("tenantId", tid));
        var audit = auditService.emit("bootstrap.closed", state.activeNationalAdminUserId(), tid, Map.of());
        return new BootstrapDtos.BootstrapFirstAdminResponse(
                "completed", state.bootstrapAccountId(), null, null,
                "Bootstrap closed", "Bootstrap Mode closed.", true, state.roleExpiresAt(),
                audit.auditStatus(), audit.auditEventId(),
                new BootstrapDtos.BootstrapPolicyDecision(true, UUID.randomUUID().toString(), List.of(), List.of())
        );
    }

    public BootstrapDtos.BootstrapFirstAdminResponse openRecovery(String tenantId, BootstrapDtos.BootstrapRecoveryRequest request) {
        String tid = tenantId != null ? tenantId : DEFAULT_TENANT;
        BootstrapDtos.BootstrapStateRecord state = stateRepository.getOrInit(tid);
        BootstrapDtos.BootstrapPolicyDecision decision = policyService.evaluateRecoveryOpen(state, request.ceremonyReference());
        if (!decision.allowed()) {
            var audit = auditService.emit("bootstrap.denied", null, tid, Map.of("reason", decision.blockedReasons()));
            return deniedResponse(decision, audit, decision.blockedReasons().get(0));
        }
        BootstrapDtos.BootstrapStateRecord updated = new BootstrapDtos.BootstrapStateRecord(
                state.id(), tid, true, false, state.bootstrapClosedAt(), true,
                OffsetDateTime.now().toString(), null, state.activeNationalAdminUserId(),
                state.bootstrapAccountId(), state.bootstrapMethod(), state.roleExpiresAt(),
                state.createdAt(), OffsetDateTime.now().toString(),
                Map.of("ceremonyReference", request.ceremonyReference(), "notes", request.notes())
        );
        stateRepository.save(tid, updated);
        workforceGovernanceClient.postJson("/v1/internal/governance/bootstrap/recovery/open", Map.of("ceremonyReference", request.ceremonyReference()));
        var audit = auditService.emit("bootstrap.recovery_opened", null, tid, Map.of("ceremonyReference", request.ceremonyReference()));
        return new BootstrapDtos.BootstrapFirstAdminResponse(
                "pending", null, null, null, "Recovery mode opened",
                "Bootstrap recovery ceremony opened. Audit recorded.", false, null,
                audit.auditStatus(), audit.auditEventId(), decision
        );
    }

    public BootstrapDtos.BootstrapFirstAdminResponse closeRecovery(String tenantId, BootstrapDtos.BootstrapRecoveryRequest request) {
        String tid = tenantId != null ? tenantId : DEFAULT_TENANT;
        BootstrapDtos.BootstrapStateRecord state = stateRepository.getOrInit(tid);
        BootstrapDtos.BootstrapStateRecord updated = new BootstrapDtos.BootstrapStateRecord(
                state.id(), tid, false, true, OffsetDateTime.now().toString(), false,
                state.recoveryOpenedAt(), OffsetDateTime.now().toString(), state.activeNationalAdminUserId(),
                state.bootstrapAccountId(), state.bootstrapMethod(), state.roleExpiresAt(),
                state.createdAt(), OffsetDateTime.now().toString(), state.metadata()
        );
        stateRepository.save(tid, updated);
        workforceGovernanceClient.postJson("/v1/internal/governance/bootstrap/recovery/close", Map.of());
        var audit = auditService.emit("bootstrap.recovery_closed", null, tid, Map.of("notes", request.notes()));
        return new BootstrapDtos.BootstrapFirstAdminResponse(
                "completed", null, null, null, "Recovery closed", "Recovery mode closed.", true, null,
                audit.auditStatus(), audit.auditEventId(),
                new BootstrapDtos.BootstrapPolicyDecision(true, UUID.randomUUID().toString(), List.of(), List.of())
        );
    }

    private boolean activeNationalAdminExists(BootstrapDtos.BootstrapStateRecord state) {
        if (state.activeNationalAdminUserId() != null && !state.activeNationalAdminUserId().isBlank()) return true;
        if (state.bootstrapClosed()) return true;
        JsonNode bootstrapState = workforceGovernanceClient.getJson("/v1/internal/governance/bootstrap/state");
        return bootstrapState != null && bootstrapState.path("activeNationalAdminExists").asBoolean(false);
    }

    private static Map<String, Object> sovereignOrganisationBody(BootstrapDtos.BootstrapFirstAdminRequest request) {
        Map<String, Object> org = request.sovereignOrganisation();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organisationCode", str(org.get("organisationCode")).isBlank() ? "MOHCC-SOVEREIGN" : str(org.get("organisationCode")));
        body.put("name", str(org.get("legalName")));
        body.put("legalName", str(org.get("legalName")));
        body.put("organisationType", str(org.get("organisationType")).isBlank() ? "sovereign_public_owner" : str(org.get("organisationType")));
        body.put("metadataJson", "{\"country\":\"Zimbabwe\",\"trustTier\":\"tier_6_sovereign_public_operator\",\"sourceRegistry\":\"bootstrap\"}");
        return body;
    }

    private static BootstrapDtos.BootstrapFirstAdminResponse deniedResponse(
            BootstrapDtos.BootstrapPolicyDecision decision, BootstrapAuditService.AuditResult audit, String message) {
        return new BootstrapDtos.BootstrapFirstAdminResponse(
                "denied", null, null, null, "Bootstrap denied", message, false, null,
                audit.auditStatus(), audit.auditEventId(), decision
        );
    }

    private static String emailFromState(BootstrapDtos.BootstrapStateRecord state) {
        Object email = state.metadata() != null ? state.metadata().get("nominatedEmail") : null;
        return email != null ? email.toString() : "bootstrap-admin";
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
