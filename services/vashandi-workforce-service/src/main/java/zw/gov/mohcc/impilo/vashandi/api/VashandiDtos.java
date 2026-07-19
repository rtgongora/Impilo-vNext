package zw.gov.mohcc.impilo.vashandi.api;

import zw.gov.mohcc.impilo.vashandi.integration.IntegrationCheckResult;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AccessRiskEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VashandiDtos {

    private VashandiDtos() {
    }

    public record CreateWorkforceProfileRequest(
            String healthId,
            String providerWorkerId,
            String keycloakUserId,
            UUID primaryOrganisationId,
            UUID primaryEmployerOrganisationId,
            String workerType,
            String profession,
            String cadre,
            String currentStatus
    ) {
    }

    public record ReconcileProfileRequest(
            UUID profileId,
            String providerWorkerId,
            String healthId,
            String keycloakUserId
    ) {
    }

    public record ReconcileProfileResponse(
            UUID profileId,
            String status,
            List<IntegrationCheckResult> dependencyChecks,
            WorkforceProfileEntity profile
    ) {
    }

    public record CreateAssignmentRequest(
            UUID workforceProfileId,
            String assignmentType,
            UUID organisationId,
            UUID facilityId,
            String departmentId,
            String unitId,
            String programmeId,
            UUID workspaceId,
            String roleTemplateId,
            UUID supervisorProfileId,
            LocalDate startDate,
            LocalDate endDate,
            String sourceAuthority,
            // Identity-program (D-P7): how the person is engaged at this posting —
            // PERMANENT | ROTATION | LOCUM | OUTREACH | TELEMED | SPECIALIST_POOL |
            // SUPERVISORY | TRAINING. A non-PERMANENT engagement must be end-dated
            // (enforced by the vsh_workforce_assignment CHECK constraint).
            String engagementType
    ) {
    }

    public record UpdateAssignmentRequest(
            String departmentId,
            String unitId,
            LocalDate endDate,
            String roleTemplateId
    ) {
    }

    public record PrecheckAssignmentRequest(String opaDecisionId) {
    }

    public record WorkforceEligibilityResult(
            UUID assignmentId,
            String overallStatus,
            String opaDecisionId,
            List<IntegrationCheckResult> checks,
            String message
    ) {
    }

    public record AssignmentActionResponse(
            UUID assignmentId,
            String assignmentStatus,
            String actionStatus,
            WorkforceEligibilityResult eligibility
    ) {
    }

    public record CreateTrainingRequirementRequest(
            String roleTemplateId,
            String courseCode,
            String enforcementLevel,
            String note
    ) {
    }

    public record CreateRosterRequest(
            UUID organisationId,
            UUID facilityId,
            String departmentId,
            String unitId,
            String rosterType,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
    }

    public record CreateShiftRequest(
            UUID rosterId,
            UUID workforceProfileId,
            UUID assignmentId,
            String shiftType,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            String locationType,
            UUID facilityId,
            String virtualPoolId,
            Boolean checkInRequired
    ) {
    }

    public record UpdateShiftRequest(
            String status,
            OffsetDateTime startTime,
            OffsetDateTime endTime
    ) {
    }

    public record CheckInRequest(
            UUID shiftId,
            UUID workforceProfileId,
            OffsetDateTime eventTime,
            String checkInMode,
            String deviceId,
            Boolean offline,
            // Optional biometric check-in: the worker's opaque biometric subject_ref
            // + a probe template. When supplied and it MATCHes, the event is recorded
            // as checkInMode="biometric"; a NO_MATCH is denied; an engine outage falls
            // back to the supplied mode (workers aren't blocked by a biometric outage).
            String biometricSubjectRef,
            String biometricModality,
            String biometricProbeBase64
    ) {
    }

    // Ad-hoc check-in — no rostered shift required; check in against an active
    // assignment (or a facility/department/unit context) directly.
    public record AdhocCheckInRequest(
            UUID workforceProfileId,
            UUID assignmentId,
            UUID facilityId,
            String departmentId,
            String unitId,
            UUID workspaceId,
            String contextScope,
            OffsetDateTime eventTime,
            String checkInMode,
            String deviceId,
            Boolean offline
    ) {
    }

    public record CheckOutRequest(
            UUID shiftId,
            UUID workforceProfileId,
            OffsetDateTime eventTime,
            String checkInMode,
            Boolean offline
    ) {
    }

    public record SupervisorConfirmRequest(
            UUID shiftId,
            UUID workforceProfileId,
            String confirmationType
    ) {
    }

    public record AttendanceActionResponse(UUID eventId, String status, String message) {
    }

    public record CreateLeaveRequest(
            UUID workforceProfileId,
            String leaveType,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String sourceAuthority
    ) {
    }

    public record UpdateLeaveRequest(String status, LocalDate endDate, String approvedBy) {
    }

    public record UpsertLeaveBalanceRequest(
            UUID workforceProfileId,
            String leaveType,
            int fiscalYear,
            int entitlement,
            int carriedOver,
            int used
    ) {
    }

    public record AccessRiskScanResponse(int detectedCount, List<AccessRiskEntity> risks, String status) {
    }

    public record AccessRiskActionResponse(UUID riskId, String status, String message) {
    }

    public record StaffingAnalyticsResponse(Map<String, Long> counts, String status) {
    }

    public record RosterCoverageResponse(
            long approvedRosters,
            long scheduledShifts,
            long checkedInShifts,
            long facilityScheduledShifts,
            String status
    ) {
    }

    public record AttendanceAnalyticsResponse(
            long checkIns,
            long checkOuts,
            long supervisorConfirmed,
            String status
    ) {
    }

    public record VacancyAnalyticsResponse(
            long uncoveredShifts,
            long scheduledShifts,
            long activeAssignments,
            String status
    ) {
    }

    public record AccessRiskAnalyticsResponse(long openRisks, long criticalRisks, long highRisks, String status) {
    }

    public record ImportWorkforceRow(
            String healthId,
            String providerWorkerId,
            String keycloakUserId,
            UUID organisationId,
            String organisationType,
            String membershipType,
            String workerType,
            String profession,
            String cadre,
            String currentStatus,
            String assignmentType,
            UUID facilityId,
            String departmentId,
            String roleTemplateId
    ) {
    }

    public record ImportBridgeRequest(String source, List<ImportWorkforceRow> rows) {
    }

    public record ImportBridgeResponse(
            int profilesImported,
            int assignmentsImported,
            List<UUID> profileIds,
            List<UUID> assignmentIds,
            String status
    ) {
    }

    // ---- C2 Workforce work-context read-model (FROZEN shape, see shared-read-models.md) ----
    // Producer: vashandi. Consumers: T1 context picker (WHERE/WHAT), Cadre Engine.

    public record WorkContextAssignment(
            UUID assignmentId,
            String status,
            UUID organisationId,
            UUID facilityId,
            String departmentId,
            String unitId,
            UUID workspaceId,
            String programmeId,
            String roleTemplateId,
            UUID supervisorProfileId,
            String scope,
            String eligibilityStatus
    ) {
    }

    public record WorkContextCheckIn(
            String shiftId,
            String state,
            OffsetDateTime at,
            UUID facilityId,
            boolean supervisorConfirmed
    ) {
    }

    public record WorkContextAffiliation(
            UUID organisationId,
            String relationshipType,
            boolean primary,
            LocalDate validFrom,
            LocalDate validTo
    ) {
    }

    public record WorkforceContextResponse(
            UUID workforceProfileId,
            String impiloHealthId,
            List<WorkContextAssignment> activeAssignments,
            WorkContextCheckIn checkIn,
            List<WorkContextAffiliation> affiliations,
            boolean requiresContextChooser,
            boolean resolved
    ) {
        public static WorkforceContextResponse unresolved() {
            return new WorkforceContextResponse(
                    null, null, List.of(),
                    new WorkContextCheckIn(null, "CHECKED_OUT", null, null, false),
                    List.of(), false, false);
        }
    }
}
