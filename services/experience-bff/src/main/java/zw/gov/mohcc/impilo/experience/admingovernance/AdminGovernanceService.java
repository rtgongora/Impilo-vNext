package zw.gov.mohcc.impilo.experience.admingovernance;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;

import java.util.*;

@Service
public class AdminGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(AdminGovernanceService.class);

    private final SessionExperienceService sessionExperienceService;
    private final AdminGovernancePolicyService policyService;
    private final AdminGovernanceAccessRequestPersistence accessRequestPersistence;
    private final AdminGovernanceAccessRequestStore accessRequestStore;
    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final TshepoAuditServiceClient auditClient;
    private final AdminGovernanceAuditHelper auditHelper;
    private final AdminGovernanceOrganisationService organisationService;
    private final AdminGovernanceLookupService lookupService;

    public AdminGovernanceService(SessionExperienceService sessionExperienceService,
                                  AdminGovernancePolicyService policyService,
                                  AdminGovernanceAccessRequestPersistence accessRequestPersistence,
                                  AdminGovernanceAccessRequestStore accessRequestStore,
                                  WorkforceGovernanceClient workforceGovernanceClient,
                                  TshepoAuditServiceClient auditClient,
                                  AdminGovernanceAuditHelper auditHelper,
                                  AdminGovernanceOrganisationService organisationService,
                                  AdminGovernanceLookupService lookupService) {
        this.sessionExperienceService = sessionExperienceService;
        this.policyService = policyService;
        this.accessRequestPersistence = accessRequestPersistence;
        this.accessRequestStore = accessRequestStore;
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.auditClient = auditClient;
        this.auditHelper = auditHelper;
        this.organisationService = organisationService;
        this.lookupService = lookupService;
    }

    public AdminGovernanceDtos.ActionResponse precheck(
            String tenantId,
            String actorId,
            String providerId,
            boolean hasSelectedFacility,
            AdminGovernanceDtos.PrecheckRequest request) {
        Map<String, Object> contract = buildContract(actorId, providerId, hasSelectedFacility);
        AdminGovernanceDtos.PolicyDecision decision = policyService.evaluate(contract, request);
        if (!decision.allowed()) {
            String message = decision.warnings().isEmpty()
                    ? "This action is blocked by policy."
                    : decision.warnings().get(0);
            return AdminGovernanceResponses.denied(message, decision);
        }
        return AdminGovernanceResponses.of(
                decision.approvalsRequired() != null && !decision.approvalsRequired().isEmpty() ? "pending" : "allowed",
                null,
                request.targetSubjectId(),
                null,
                decision.approvalsRequired() != null && !decision.approvalsRequired().isEmpty()
                        ? "Approval required"
                        : "Action permitted",
                decision.approvalsRequired() != null && !decision.approvalsRequired().isEmpty()
                        ? "This action requires approval before it can complete."
                        : "Policy precheck passed for your current authority.",
                List.of("Submit", "View My Scope"),
                null,
                decision,
                null,
                "live",
                Map.of("warnings", decision.warnings(), "approvalsRequired", decision.approvalsRequired())
        );
    }

    public List<AdminGovernanceDtos.AccessRequestRecord> listAccessRequests(String tenantId, String actorId) {
        return accessRequestPersistence.list(tenantId).stream()
                .filter(r -> isVisibleToActor(r, actorId))
                .toList();
    }

    public Optional<AdminGovernanceDtos.AccessRequestRecord> getAccessRequest(String tenantId, String requestId) {
        return accessRequestPersistence.find(tenantId, requestId);
    }

    public AdminGovernanceDtos.ActionResponse submitOnboarding(
            String tenantId,
            String actorId,
            String providerId,
            boolean hasSelectedFacility,
            AdminGovernanceDtos.OnboardingSubmission submission) {
        String action = onboardingAction(submission.flowId());
        AdminGovernanceDtos.PrecheckRequest precheck = new AdminGovernanceDtos.PrecheckRequest(
                action,
                "/work/administration-governance/onboard/" + submission.flowId(),
                submission.organisationId(),
                submission.targetProviderWorkerId() != null ? submission.targetProviderWorkerId() : submission.targetHealthId(),
                submission.roleTemplate(),
                submission.requestedScope(),
                submission.requestedEnvironment(),
                submission.requestedDataScope(),
                submission.requestedDurationDays(),
                submission.crossBorderFlag(),
                riskForFlow(submission.flowId()),
                submission.profile()
        );
        AdminGovernanceDtos.PolicyDecision decision = policyService.evaluate(buildContract(actorId, providerId, hasSelectedFacility), precheck);
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied(
                    decision.warnings().isEmpty() ? "Policy denied onboarding." : decision.warnings().get(0), decision);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("flowId", submission.flowId());
        payload.put("organisationId", submission.organisationId());
        payload.put("targetHealthId", submission.targetHealthId());
        payload.put("targetSubjectId", submission.targetProviderWorkerId());
        payload.put("roleTemplate", submission.roleTemplate());
        payload.put("requestedScope", submission.requestedScope());
        payload.put("requestedEnvironment", submission.requestedEnvironment());
        payload.put("requestedDataScope", submission.requestedDataScope());
        payload.put("riskLevel", riskForFlow(submission.flowId()));
        if (submission.profile() != null) payload.putAll(submission.profile());

        AdminGovernanceDtos.AccessRequestRecord record = AdminGovernanceAccessRequestStore.newRecord(
                "onboarding." + submission.flowId(),
                actorId,
                actorId,
                payload,
                decision
        );
        record = accessRequestPersistence.save(tenantId, record, actorId);
        var audit = auditHelper.emit("onboarding.submitted", actorId, record.id(), payload);

        boolean pending = decision.approvalsRequired() != null && !decision.approvalsRequired().isEmpty();
        boolean fallback = Boolean.TRUE.equals(record.reconciliationRequired());
        return AdminGovernanceResponses.of(
                pending ? "pending" : "submitted",
                record.id(),
                submission.targetProviderWorkerId() != null ? submission.targetProviderWorkerId() : submission.targetHealthId(),
                null,
                pending ? "Access request submitted" : "Onboarding submitted",
                fallback
                        ? "This request is queued in BFF fallback storage and will require reconciliation with workforce-governance."
                        : pending
                        ? "This request requires approval before it can complete."
                        : outcomeMessage(submission.flowId()),
                List.of("View access request", "Return to Administration & Governance"),
                null,
                decision,
                audit,
                fallback ? "pending_backend" : "live",
                Map.of("request", record, "sourceOfRecordStatus", record.sourceOfRecordStatus())
        );
    }

    public AdminGovernanceDtos.ActionResponse decideAccessRequest(
            String tenantId,
            String actorId,
            String providerId,
            boolean hasSelectedFacility,
            String requestId,
            AdminGovernanceDtos.AccessRequestDecisionRequest decisionRequest) {
        AdminGovernanceDtos.AccessRequestRecord existing = accessRequestPersistence.find(tenantId, requestId).orElse(null);
        if (existing == null) {
            return AdminGovernanceResponses.pendingBackend("Access request store unavailable or request not found.", null);
        }

        AdminGovernanceDtos.PrecheckRequest precheck = new AdminGovernanceDtos.PrecheckRequest(
                "ACCESS_REQUEST_DECIDE",
                "/work/administration-governance/access-requests",
                existing.organisationId(),
                existing.targetSubjectId(),
                existing.requestedRole(),
                existing.requestedScope(),
                existing.requestedEnvironment(),
                existing.requestedDataScope(),
                null,
                null,
                existing.riskLevel(),
                Map.of("decision", decisionRequest.decision())
        );
        AdminGovernanceDtos.PolicyDecision decision = policyService.evaluate(buildContract(actorId, providerId, hasSelectedFacility), precheck);
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied("You do not have authority to decide this request.", decision);
        }

        String newStatus = switch (normalize(decisionRequest.decision())) {
            case "approve" -> "APPROVED";
            case "reject", "deny" -> "REJECTED";
            case "request_more_information" -> "NEEDS_INFO";
            case "escalate" -> "ESCALATED";
            case "revoke" -> "REVOKED";
            default -> "PENDING";
        };

        AdminGovernanceDtos.AccessRequestRecord updated = new AdminGovernanceDtos.AccessRequestRecord(
                existing.id(),
                existing.requestType(),
                newStatus,
                existing.requesterId(),
                existing.requesterName(),
                existing.organisationId(),
                existing.organisationName(),
                existing.targetSubjectId(),
                existing.requestedRole(),
                existing.requestedScope(),
                existing.requestedEnvironment(),
                existing.requestedDataScope(),
                existing.riskLevel(),
                existing.policyPrecheckResult(),
                existing.approvalsRequired(),
                appendTrail(existing.auditTrail(), Map.of(
                        "event", "access_request." + normalize(decisionRequest.decision()),
                        "by", actorId,
                        "notes", decisionRequest.notes() != null ? decisionRequest.notes() : ""
                )),
                existing.createdAt(),
                java.time.OffsetDateTime.now().toString(),
                existing.payload(),
                existing.sourceOfRecordStatus(),
                existing.fallbackRequestId(),
                existing.durableRequestId(),
                existing.downstreamCorrelationId(),
                existing.reconciliationRequired(),
                existing.lastSyncAttemptAt(),
                existing.lastSyncStatus()
        );
        updated = accessRequestPersistence.decide(tenantId, updated, decisionRequest.decision(), actorId, decisionRequest.notes());
        var audit = auditHelper.emit("access_request." + normalize(decisionRequest.decision()), actorId, requestId, Map.of("status", newStatus));

        return AdminGovernanceResponses.of(
                "completed",
                requestId,
                updated.targetSubjectId(),
                null,
                friendlyDecisionTitle(newStatus),
                friendlyDecisionMessage(newStatus),
                List.of("View access requests", "Audit review"),
                null,
                decision,
                audit,
                "live",
                Map.of("request", updated)
        );
    }

    public AdminGovernanceDtos.ActionResponse createFacilityAssignment(
            String tenantId,
            String actorId,
            String providerId,
            boolean hasSelectedFacility,
            AdminGovernanceDtos.FacilityStaffAssignmentRequest request) {
        AdminGovernanceDtos.LookupEnvelope eligibility = lookupService.providerEligibility(request.providerWorkerId());
        if (!"live".equals(eligibility.integrationStatus())) {
            return AdminGovernanceResponses.pendingBackend(eligibility.friendlyMessage(), null);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> eligibilityData = (Map<String, Object>) eligibility.data().get("eligibility");
        @SuppressWarnings("unchecked")
        List<String> blockedReasons = eligibilityData != null
                ? (List<String>) eligibilityData.getOrDefault("blockedReasons", List.of())
                : List.of();

        AdminGovernanceDtos.PrecheckRequest precheck = new AdminGovernanceDtos.PrecheckRequest(
                "FACILITY_STAFF_ASSIGN",
                "/work/facility/" + request.facilityId() + "/staff-access/add",
                null,
                request.providerWorkerId(),
                request.roleTemplate(),
                request.departmentId(),
                null,
                null,
                null,
                null,
                "MEDIUM",
                Map.of("facilityId", request.facilityId(), "workspaceId", request.workspaceId(), "eligibility", eligibilityData)
        );
        AdminGovernanceDtos.PolicyDecision decision = policyService.evaluate(buildContract(actorId, providerId, hasSelectedFacility), precheck);
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied(
                    decision.warnings().isEmpty() ? "Assignment blocked by policy." : decision.warnings().get(0), decision);
        }
        if (!blockedReasons.isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("facilityId", request.facilityId());
            payload.put("providerWorkerId", request.providerWorkerId());
            payload.put("roleTemplate", request.roleTemplate());
            payload.put("blockedReasons", blockedReasons);
            AdminGovernanceDtos.AccessRequestRecord record = accessRequestPersistence.save(
                    tenantId,
                    AdminGovernanceAccessRequestStore.newRecord("facility_assignment", actorId, actorId, payload, decision),
                    actorId);
            var audit = auditHelper.emit("facility_assignment.queued", actorId, record.id(), payload);
            return AdminGovernanceResponses.of(
                    "pending",
                    record.id(),
                    request.providerWorkerId(),
                    null,
                    "Assignment requires approval",
                    String.join("; ", blockedReasons),
                    List.of("View access request"),
                    "eligibility_blocked",
                    decision,
                    audit,
                    Boolean.TRUE.equals(record.reconciliationRequired()) ? "pending_backend" : "live",
                    Map.of("request", record, "eligibility", eligibilityData)
            );
        }

        Map<String, Object> assignmentBody = new LinkedHashMap<>();
        assignmentBody.put("subjectType", "PROVIDER");
        assignmentBody.put("subjectId", request.providerWorkerId());
        assignmentBody.put("targetType", "FACILITY");
        assignmentBody.put("targetId", request.facilityId());
        assignmentBody.put("initialStatus", request.assumptionOfDutyRequired() ? "PENDING_ASSUMPTION" : "ACTIVE");
        assignmentBody.put("scopeSummary", request.departmentId());
        assignmentBody.put("notes", request.roleTemplate());

        JsonNode downstream = workforceGovernanceClient.postJson("/v1/internal/governance/assignments", assignmentBody);
        if (downstream == null || downstream.isNull()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("facilityId", request.facilityId());
            payload.put("providerWorkerId", request.providerWorkerId());
            payload.put("roleTemplate", request.roleTemplate());
            AdminGovernanceDtos.AccessRequestRecord record = accessRequestPersistence.save(
                    tenantId,
                    AdminGovernanceAccessRequestStore.newRecord("facility_assignment", actorId, actorId, payload, decision),
                    actorId);
            var audit = auditHelper.emit("facility_assignment.created", actorId, record.id(), payload);
            return AdminGovernanceResponses.of(
                    "pending",
                    record.id(),
                    request.providerWorkerId(),
                    null,
                    "Assignment queued for approval",
                    "Workforce governance service is unavailable — an access request was recorded for follow-up.",
                    List.of("View access request"),
                    "pending_backend",
                    decision,
                    audit,
                    "pending_backend",
                    Map.of("request", record)
            );
        }

        String assignmentId = downstream.has("id") ? downstream.get("id").asText() : null;
        var audit = auditHelper.emit("facility_assignment.created", actorId, assignmentId, assignmentBody);
        return AdminGovernanceResponses.of(
                request.assumptionOfDutyRequired() ? "pending" : "completed",
                null,
                request.providerWorkerId(),
                assignmentId,
                request.assumptionOfDutyRequired() ? "Assignment pending assumption of duty" : "Assignment created",
                request.assumptionOfDutyRequired()
                        ? "Work appears only after assumption of duty is recorded."
                        : "Verified provider/worker assignment recorded. Work activates when policy allows.",
                List.of("View facility staff", "Access requests"),
                null,
                decision,
                audit,
                "live",
                Map.of("assignment", downstream, "eligibility", eligibilityData)
        );
    }

    public AdminGovernanceDtos.ActionResponse domainAction(
            String tenantId,
            String actorId,
            String providerId,
            boolean hasSelectedFacility,
            String action,
            Map<String, Object> body) {
        AdminGovernanceDtos.PrecheckRequest precheck = new AdminGovernanceDtos.PrecheckRequest(
                action,
                str(body.get("sourcePage")),
                str(body.get("organisationId")),
                str(body.get("targetSubjectId")),
                str(body.get("roleTemplate")),
                str(body.get("requestedScope")),
                str(body.get("requestedEnvironment")),
                str(body.get("requestedDataScope")),
                body.get("requestedDurationDays") instanceof Number n ? n.intValue() : null,
                body.get("crossBorderFlag") instanceof Boolean b ? b : null,
                str(body.get("riskLevel")),
                body
        );
        AdminGovernanceDtos.PolicyDecision decision = policyService.evaluate(buildContract(actorId, providerId, hasSelectedFacility), precheck);
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied(decision.warnings().isEmpty() ? "Action blocked." : decision.warnings().get(0), decision);
        }

        if ("HSC_EMPLOYMENT_UPDATE".equals(action)) {
            JsonNode hsc = workforceGovernanceClient.postJson("/v1/internal/governance/hsc/employment-records", body);
            if (hsc == null || hsc.isNull()) {
                return queueDomainAccessRequest(tenantId, actorId, action, body, decision);
            }
            var audit = auditHelper.emit(action.toLowerCase(Locale.ROOT), actorId, hsc.path("id").asText(), body);
            return AdminGovernanceResponses.of(
                    "completed", null, str(body.get("targetSubjectId")), null,
                    "HSC employment updated", "Public-sector employment record updated.",
                    List.of("View HSC records"), null, decision, audit, "live", Map.of("employment", hsc));
        }

        return queueDomainAccessRequest(tenantId, actorId, action, body, decision);
    }

    public List<Map<String, Object>> listAuditEvents(String tenantId, String actorId) {
        try {
            JsonNode events = auditClient.queryEvents(actorId, null, null, null, null, 0, 20);
            if (events == null || events.isNull()) return List.of();
            if (events.isArray()) {
                List<Map<String, Object>> out = new ArrayList<>();
                events.forEach(node -> out.add(Map.of(
                        "id", node.path("id").asText(""),
                        "eventType", node.path("eventType").asText(""),
                        "actorId", node.path("actorId").asText(""),
                        "occurredAt", node.path("occurredAt").asText("")
                )));
                return out;
            }
        } catch (Exception e) {
            log.warn("Audit query failed: {}", e.getMessage());
        }
        return accessRequestStore.list(tenantId).stream()
                .flatMap(r -> r.auditTrail().stream())
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("eventType", String.valueOf(entry.get("event")));
                    row.put("occurredAt", String.valueOf(entry.get("at")));
                    row.put("integrationStatus", "bff_access_request_trail");
                    return row;
                })
                .toList();
    }

    public AdminGovernanceDtos.LookupEnvelope listOrganisations(String tenantId, String actorId, String providerId, boolean hasFacility) {
        return organisationService.listOrganisations(tenantId, actorId, providerId, hasFacility);
    }

    public AdminGovernanceDtos.ActionResponse createOrganisation(String tenantId, String actorId, String providerId, boolean hasFacility,
                                                                 AdminGovernanceDtos.OrganisationUpsertRequest request) {
        return organisationService.createOrganisation(tenantId, actorId, providerId, hasFacility, request);
    }

    public AdminGovernanceDtos.LookupEnvelope getOrganisation(String orgId) {
        return organisationService.getOrganisation(orgId);
    }

    public AdminGovernanceDtos.ActionResponse organisationLifecycle(String tenantId, String actorId, String providerId, boolean hasFacility,
                                                                    String orgId, String action, String sourcePage) {
        return organisationService.organisationLifecycleAction(tenantId, actorId, providerId, hasFacility, orgId, action, sourcePage);
    }

    public AdminGovernanceDtos.LookupEnvelope listOrganisationUsers(String orgId) {
        return organisationService.listOrganisationUsers(orgId);
    }

    public AdminGovernanceDtos.ActionResponse organisationUserAction(String tenantId, String actorId, String providerId, boolean hasFacility,
                                                                     String orgId, String userId, String action,
                                                                     AdminGovernanceDtos.OrganisationUserRequest request, String sourcePage) {
        return organisationService.organisationUserAction(actorId, providerId, hasFacility, orgId, userId, action, request, sourcePage);
    }

    public AdminGovernanceDtos.LookupEnvelope searchProviderWorkers(String q, String providerWorkerId, String healthId, String profession,
                                                                     String cadre, String council, String status, String facilityId, String organisationId) {
        return lookupService.searchProviderWorkers(q, providerWorkerId, healthId, profession, cadre, council, status, facilityId, organisationId);
    }

    public AdminGovernanceDtos.LookupEnvelope providerEligibility(String providerWorkerId) {
        return lookupService.providerEligibility(providerWorkerId);
    }

    public AdminGovernanceDtos.LookupEnvelope searchHscEmployment(String providerWorkerId, String healthId, String employeeNumber,
                                                                  String facilityId, String province, String district, String status) {
        return lookupService.searchHscEmployment(providerWorkerId, healthId, employeeNumber, facilityId, province, district, status);
    }

    public AdminGovernanceDtos.LookupEnvelope getHscEmployment(String employmentRecordId) {
        return lookupService.getHscEmployment(employmentRecordId);
    }

    public AdminGovernanceDtos.LookupEnvelope professionalStatus(String providerWorkerId, String healthId, String council,
                                                                 String registrationNumber, String profession, String cadre) {
        return lookupService.professionalStatus(providerWorkerId, healthId, council, registrationNumber, profession, cadre);
    }

    private AdminGovernanceDtos.ActionResponse queueDomainAccessRequest(String tenantId, String actorId, String action,
                                                                        Map<String, Object> body, AdminGovernanceDtos.PolicyDecision decision) {
        AdminGovernanceDtos.AccessRequestRecord record = accessRequestPersistence.save(
                tenantId,
                AdminGovernanceAccessRequestStore.newRecord(action.toLowerCase(Locale.ROOT), actorId, actorId, body, decision),
                actorId);
        var audit = auditHelper.emit(action.toLowerCase(Locale.ROOT), actorId, record.id(), body);
        boolean pending = decision.approvalsRequired() != null && !decision.approvalsRequired().isEmpty();
        boolean fallback = Boolean.TRUE.equals(record.reconciliationRequired());
        return AdminGovernanceResponses.of(
                pending ? "pending" : "submitted",
                record.id(),
                str(body.get("targetSubjectId")),
                null,
                pending ? "Access request submitted" : "Action submitted",
                fallback
                        ? "This request is queued in BFF fallback storage and will require reconciliation with workforce-governance."
                        : pending ? "This request requires approval." : "Action recorded for workflow processing.",
                List.of("View access request"),
                null,
                decision,
                audit,
                fallback ? "pending_backend" : "live",
                Map.of("request", record)
        );
    }

    private Map<String, Object> buildContract(String actorId, String providerId, boolean hasSelectedFacility) {
        return sessionExperienceService.buildExperienceContract(actorId, null, providerId, hasSelectedFacility);
    }

    private static boolean isVisibleToActor(AdminGovernanceDtos.AccessRequestRecord record, String actorId) {
        return Objects.equals(record.requesterId(), actorId)
                || "PENDING".equals(record.status())
                || "NEEDS_INFO".equals(record.status())
                || "ESCALATED".equals(record.status());
    }

    private static String onboardingAction(String flowId) {
        return switch (flowId) {
            case "citizen" -> "ONBOARD_CITIZEN";
            case "provider-worker" -> "ONBOARD_PROVIDER_WORKER";
            case "public-sector-worker" -> "ONBOARD_PUBLIC_SECTOR_WORKER";
            case "hsc-user" -> "ONBOARD_HSC_USER";
            case "municipal-user" -> "ONBOARD_MUNICIPAL_USER";
            case "private-facility-user" -> "ONBOARD_PRIVATE_FACILITY_USER";
            case "regulator-user" -> "ONBOARD_REGULATOR_USER";
            case "madi-user" -> "ONBOARD_MADI_USER";
            case "marketplace-user" -> "ONBOARD_MARKETPLACE_USER";
            case "payer-user" -> "ONBOARD_PAYER_USER";
            case "training-user" -> "ONBOARD_TRAINING_USER";
            case "external-partner-user" -> "ONBOARD_EXTERNAL_PARTNER";
            case "system-admin" -> "ONBOARD_SYSTEM_ADMIN";
            default -> "ONBOARD_" + flowId.toUpperCase(Locale.ROOT).replace('-', '_');
        };
    }

    private static String riskForFlow(String flowId) {
        return switch (flowId) {
            case "system-admin", "external-partner-user" -> "HIGH";
            case "marketplace-user", "payer-user" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private static String outcomeMessage(String flowId) {
        return switch (flowId) {
            case "citizen" -> "Creates Personal access only — no Professional or Work.";
            case "provider-worker" -> "Creates or verifies Professional access. Work requires a separate assignment.";
            case "hsc-user" -> "Creates HSC Workforce Governance Work — not clinical Work.";
            case "regulator-user" -> "Creates Regulatory Work within council scope.";
            case "marketplace-user" -> "Marketplace Work respects sandbox/production separation.";
            default -> "Onboarding request recorded under your authority scope.";
        };
    }

    private static String friendlyDecisionTitle(String status) {
        return switch (status) {
            case "APPROVED" -> "Access request approved";
            case "REJECTED" -> "Access request rejected";
            case "NEEDS_INFO" -> "More information requested";
            case "ESCALATED" -> "Access request escalated";
            case "REVOKED" -> "Access revoked";
            default -> "Decision recorded";
        };
    }

    private static String friendlyDecisionMessage(String status) {
        return switch (status) {
            case "APPROVED" -> "The request was approved within your authority scope.";
            case "REJECTED" -> "The request was rejected and the requester can review the decision.";
            case "NEEDS_INFO" -> "The requester has been asked to provide more information.";
            case "ESCALATED" -> "The request was escalated to a higher authority.";
            case "REVOKED" -> "Access was revoked and audit was recorded.";
            default -> "Request status updated.";
        };
    }

    private static List<Map<String, Object>> appendTrail(List<Map<String, Object>> existing, Map<String, Object> entry) {
        List<Map<String, Object>> trail = new ArrayList<>(existing != null ? existing : List.of());
        trail.add(new LinkedHashMap<>(entry));
        return trail;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
