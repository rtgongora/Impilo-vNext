package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceDtos;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceImportService;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceAuthorisedRepresentativeService;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administration & Governance BFF — OPA-prechecked workflows for scoped management actions.
 */
@RestController
@RequestMapping("/internal/v1/admin-governance")
public class AdminGovernanceController {

    private final AdminGovernanceService adminGovernanceService;
    private final AdminGovernanceImportService importService;
    private final AdminGovernanceAuthorisedRepresentativeService authorisedRepresentativeService;

    public AdminGovernanceController(AdminGovernanceService adminGovernanceService,
                                     AdminGovernanceImportService importService,
                                     AdminGovernanceAuthorisedRepresentativeService authorisedRepresentativeService) {
        this.adminGovernanceService = adminGovernanceService;
        this.importService = importService;
        this.authorisedRepresentativeService = authorisedRepresentativeService;
    }

    @PostMapping("/precheck")
    public ResponseEntity<Map<String, Object>> precheck(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestBody AdminGovernanceDtos.PrecheckRequest body) {
        return ok(requestId, correlationId, adminGovernanceService.precheck(
                tenantId, actorId, providerId, hasFacility(facilityId), body));
    }

    @GetMapping("/access-requests")
    public ResponseEntity<Map<String, Object>> listAccessRequests(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        List<AdminGovernanceDtos.AccessRequestRecord> items = adminGovernanceService.listAccessRequests(tenantId, actorId);
        return ok(requestId, correlationId, Map.of("items", items));
    }

    @GetMapping("/access-requests/{requestId}")
    public ResponseEntity<Map<String, Object>> getAccessRequest(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String requestIdPath) {
        return adminGovernanceService.getAccessRequest(tenantId, requestIdPath)
                .map(record -> ok(requestId, correlationId, record))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(requestId, correlationId, "NOT_FOUND", "Access request not found")));
    }

    @PostMapping("/onboarding/{flowId}")
    public ResponseEntity<Map<String, Object>> submitOnboarding(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String flowId,
            @RequestBody AdminGovernanceDtos.OnboardingSubmission body) {
        AdminGovernanceDtos.OnboardingSubmission submission = new AdminGovernanceDtos.OnboardingSubmission(
                flowId,
                body.organisationId(),
                body.targetHealthId(),
                body.targetProviderWorkerId(),
                body.roleTemplate(),
                body.requestedScope(),
                body.requestedEnvironment(),
                body.requestedDataScope(),
                body.requestedDurationDays(),
                body.crossBorderFlag(),
                body.purpose(),
                body.country(),
                body.profile()
        );
        return ok(requestId, correlationId, adminGovernanceService.submitOnboarding(
                tenantId, actorId, providerId, hasFacility(facilityId), submission));
    }

    @PostMapping("/access-requests/{requestId}/{decision}")
    public ResponseEntity<Map<String, Object>> decideAccessRequest(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String requestIdPath,
            @PathVariable String decision,
            @RequestBody(required = false) AdminGovernanceDtos.AccessRequestDecisionRequest body) {
        AdminGovernanceDtos.AccessRequestDecisionRequest req = body != null
                ? body
                : new AdminGovernanceDtos.AccessRequestDecisionRequest(decision, null, null);
        return ok(requestId, correlationId, adminGovernanceService.decideAccessRequest(
                tenantId, actorId, providerId, hasFacility(facilityId), requestIdPath,
                new AdminGovernanceDtos.AccessRequestDecisionRequest(decision, req.notes(), req.escalationTarget())));
    }

    @PostMapping("/facility/{facilityId}/staff-assignments")
    public ResponseEntity<Map<String, Object>> createFacilityAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String facilityIdPath,
            @RequestBody AdminGovernanceDtos.FacilityStaffAssignmentRequest body) {
        AdminGovernanceDtos.FacilityStaffAssignmentRequest req = new AdminGovernanceDtos.FacilityStaffAssignmentRequest(
                facilityIdPath,
                body.providerWorkerId(),
                body.departmentId(),
                body.workspaceId(),
                body.roleTemplate(),
                body.startDate(),
                body.endDate(),
                body.assumptionOfDutyRequired()
        );
        return ok(requestId, correlationId, adminGovernanceService.createFacilityAssignment(
                tenantId, actorId, providerId, hasFacility(facilityId), req));
    }

    @PostMapping("/actions/{action}")
    public ResponseEntity<Map<String, Object>> domainAction(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String action,
            @RequestBody Map<String, Object> body) {
        return ok(requestId, correlationId, adminGovernanceService.domainAction(
                tenantId, actorId, providerId, hasFacility(facilityId), action, body != null ? body : Map.of()));
    }

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> listAudit(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ok(requestId, correlationId, Map.of("items", adminGovernanceService.listAuditEvents(tenantId, actorId)));
    }

    @GetMapping("/organisations")
    public ResponseEntity<Map<String, Object>> listOrganisations(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId) {
        return ok(requestId, correlationId, adminGovernanceService.listOrganisations(tenantId, actorId, providerId, hasFacility(facilityId)));
    }

    @PostMapping("/organisations")
    public ResponseEntity<Map<String, Object>> createOrganisation(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestBody AdminGovernanceDtos.OrganisationUpsertRequest body) {
        return ok(requestId, correlationId, adminGovernanceService.createOrganisation(tenantId, actorId, providerId, hasFacility(facilityId), body));
    }

    @GetMapping("/organisations/{orgId}")
    public ResponseEntity<Map<String, Object>> getOrganisation(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orgId) {
        return ok(requestId, correlationId, adminGovernanceService.getOrganisation(orgId));
    }

    @PostMapping("/organisations/{orgId}/verify")
    public ResponseEntity<Map<String, Object>> verifyOrganisation(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String orgId) {
        return ok(requestId, correlationId, adminGovernanceService.organisationLifecycle(
                tenantId, actorId, providerId, hasFacility(facilityId), orgId, "verify",
                "/work/administration-governance/organisations/" + orgId + "/verification"));
    }

    @PostMapping("/organisations/{orgId}/suspend")
    public ResponseEntity<Map<String, Object>> suspendOrganisation(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String orgId) {
        return ok(requestId, correlationId, adminGovernanceService.organisationLifecycle(
                tenantId, actorId, providerId, hasFacility(facilityId), orgId, "suspend",
                "/work/administration-governance/organisations/" + orgId));
    }

    @PostMapping("/organisations/{orgId}/offboard")
    public ResponseEntity<Map<String, Object>> offboardOrganisation(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String orgId) {
        return ok(requestId, correlationId, adminGovernanceService.organisationLifecycle(
                tenantId, actorId, providerId, hasFacility(facilityId), orgId, "offboard",
                "/work/administration-governance/organisations/" + orgId));
    }

    @GetMapping("/organisations/{orgId}/users")
    public ResponseEntity<Map<String, Object>> listOrganisationUsers(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orgId) {
        return ok(requestId, correlationId, adminGovernanceService.listOrganisationUsers(orgId));
    }

    @PostMapping("/organisations/{orgId}/users")
    public ResponseEntity<Map<String, Object>> addOrganisationUser(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String orgId,
            @RequestBody AdminGovernanceDtos.OrganisationUserRequest body) {
        return ok(requestId, correlationId, adminGovernanceService.organisationUserAction(
                tenantId, actorId, providerId, hasFacility(facilityId), orgId, body.userId(), "add", body,
                "/work/administration-governance/organisations/" + orgId + "/users"));
    }

    @PostMapping("/organisations/{orgId}/users/{userId}/suspend")
    public ResponseEntity<Map<String, Object>> suspendOrganisationUser(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String orgId,
            @PathVariable String userId) {
        return ok(requestId, correlationId, adminGovernanceService.organisationUserAction(
                tenantId, actorId, providerId, hasFacility(facilityId), orgId, userId, "suspend",
                new AdminGovernanceDtos.OrganisationUserRequest(userId, null, "SUSPENDED"),
                "/work/administration-governance/organisations/" + orgId + "/users"));
    }

    @PostMapping("/organisations/{orgId}/users/{userId}/offboard")
    public ResponseEntity<Map<String, Object>> offboardOrganisationUser(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String orgId,
            @PathVariable String userId) {
        return ok(requestId, correlationId, adminGovernanceService.organisationUserAction(
                tenantId, actorId, providerId, hasFacility(facilityId), orgId, userId, "offboard",
                new AdminGovernanceDtos.OrganisationUserRequest(userId, null, "OFFBOARDED"),
                "/work/administration-governance/organisations/" + orgId + "/users"));
    }

    @GetMapping("/provider-workers/search")
    public ResponseEntity<Map<String, Object>> searchProviderWorkers(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String providerWorkerId,
            @RequestParam(required = false) String healthId,
            @RequestParam(required = false) String profession,
            @RequestParam(required = false) String cadre,
            @RequestParam(required = false) String council,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String organisationId) {
        return ok(requestId, correlationId, adminGovernanceService.searchProviderWorkers(
                q, providerWorkerId, healthId, profession, cadre, council, status, facilityId, organisationId));
    }

    @GetMapping("/provider-workers/{providerWorkerId}/eligibility")
    public ResponseEntity<Map<String, Object>> providerEligibility(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String providerWorkerId) {
        return ok(requestId, correlationId, adminGovernanceService.providerEligibility(providerWorkerId));
    }

    @GetMapping("/hsc/employment-records/search")
    public ResponseEntity<Map<String, Object>> searchHscEmployment(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String providerWorkerId,
            @RequestParam(required = false) String healthId,
            @RequestParam(required = false) String employeeNumber,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String status) {
        return ok(requestId, correlationId, adminGovernanceService.searchHscEmployment(
                providerWorkerId, healthId, employeeNumber, facilityId, province, district, status));
    }

    @GetMapping("/hsc/employment-records/{employmentRecordId}")
    public ResponseEntity<Map<String, Object>> getHscEmployment(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String employmentRecordId) {
        return ok(requestId, correlationId, adminGovernanceService.getHscEmployment(employmentRecordId));
    }

    @GetMapping("/regulators/professional-status")
    public ResponseEntity<Map<String, Object>> professionalStatus(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String providerWorkerId,
            @RequestParam(required = false) String healthId,
            @RequestParam(required = false) String council,
            @RequestParam(required = false) String registrationNumber,
            @RequestParam(required = false) String profession,
            @RequestParam(required = false) String cadre) {
        return ok(requestId, correlationId, adminGovernanceService.professionalStatus(
                providerWorkerId, healthId, council, registrationNumber, profession, cadre));
    }

    @GetMapping("/organisations/{orgId}/authorised-representatives")
    public ResponseEntity<Map<String, Object>> listAuthorisedRepresentatives(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String orgId) {
        return ok(requestId, correlationId, authorisedRepresentativeService.list(orgId));
    }

    @PostMapping("/organisations/{orgId}/authorised-representatives")
    public ResponseEntity<Map<String, Object>> inviteAuthorisedRepresentative(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String orgId,
            @RequestBody Map<String, Object> body) {
        return ok(requestId, correlationId, authorisedRepresentativeService.invite(actorId, providerId, hasFacility(facilityId), orgId, body));
    }

    @PostMapping("/organisations/{orgId}/authorised-representatives/{repId}/suspend")
    public ResponseEntity<Map<String, Object>> suspendAuthorisedRepresentative(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String orgId,
            @PathVariable String repId) {
        return ok(requestId, correlationId, authorisedRepresentativeService.lifecycle(actorId, providerId, hasFacility(facilityId), orgId, repId, "suspend"));
    }

    @PostMapping("/organisations/{orgId}/authorised-representatives/{repId}/revoke")
    public ResponseEntity<Map<String, Object>> revokeAuthorisedRepresentative(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String orgId,
            @PathVariable String repId) {
        return ok(requestId, correlationId, authorisedRepresentativeService.lifecycle(actorId, providerId, hasFacility(facilityId), orgId, repId, "revoke"));
    }

    @PostMapping("/imports")
    public ResponseEntity<Map<String, Object>> uploadImport(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestBody Map<String, Object> body) {
        return ok(requestId, correlationId, importService.upload(
                actorId, providerId, hasFacility(facilityId),
                String.valueOf(body.get("organisationId")),
                String.valueOf(body.get("importType")),
                String.valueOf(body.get("sourceFileName")),
                String.valueOf(body.get("csvContent"))));
    }

    @GetMapping("/imports")
    public ResponseEntity<Map<String, Object>> listImports(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String organisationId) {
        return ok(requestId, correlationId, importService.list(organisationId));
    }

    @PostMapping("/imports/{importBatchId}/approve")
    public ResponseEntity<Map<String, Object>> approveImport(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String importBatchId) {
        return ok(requestId, correlationId, importService.approve(actorId, importBatchId));
    }

    @PostMapping("/imports/{importBatchId}/send-invitations")
    public ResponseEntity<Map<String, Object>> sendImportInvitations(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String importBatchId) {
        return ok(requestId, correlationId, importService.sendInvitations(actorId, importBatchId));
    }

    @GetMapping("/imports/templates/{importType}")
    public ResponseEntity<Map<String, Object>> importTemplate(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String importType) {
        return ok(requestId, correlationId, importService.template(importType));
    }

    private static boolean hasFacility(String facilityId) {
        return facilityId != null && !facilityId.isBlank();
    }

    private static ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, Object attributes) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "type", "admin-governance-action",
                "attributes", attributes
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static Map<String, Object> error(String requestId, String correlationId, String code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", Map.of("code", code, "message", message));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }
}
