package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AccessRiskRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.AttendanceEventRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.RosterRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkforceAnalyticsService {

    private final WorkforceProfileRepository profileRepository;
    private final WorkforceAssignmentRepository assignmentRepository;
    private final RosterRepository rosterRepository;
    private final ShiftRepository shiftRepository;
    private final AttendanceEventRepository attendanceRepository;
    private final AccessRiskRepository accessRiskRepository;

    public WorkforceAnalyticsService(WorkforceProfileRepository profileRepository,
                                     WorkforceAssignmentRepository assignmentRepository,
                                     RosterRepository rosterRepository,
                                     ShiftRepository shiftRepository,
                                     AttendanceEventRepository attendanceRepository,
                                     AccessRiskRepository accessRiskRepository) {
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.rosterRepository = rosterRepository;
        this.shiftRepository = shiftRepository;
        this.attendanceRepository = attendanceRepository;
        this.accessRiskRepository = accessRiskRepository;
    }

    public VashandiDtos.StaffingAnalyticsResponse staffing(UUID tenantId, UUID facilityId) {
        long activeProfiles = profileRepository.countByTenantIdAndCurrentStatus(tenantId, "active");
        long activeAssignments = assignmentRepository.countByTenantIdAndStatus(tenantId, "active");
        long pendingAssignments = assignmentRepository.countByTenantIdAndStatus(tenantId, "pending_approval");
        Map<String, Long> byStatus = new HashMap<>();
        byStatus.put("active_profiles", activeProfiles);
        byStatus.put("active_assignments", activeAssignments);
        byStatus.put("pending_assignments", pendingAssignments);
        if (facilityId != null) {
            byStatus.put("facility_active_assignments",
                    (long) assignmentRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, facilityId, "active").size());
        }
        return new VashandiDtos.StaffingAnalyticsResponse(byStatus, "completed");
    }

    public VashandiDtos.RosterCoverageResponse rosterCoverage(UUID tenantId, UUID facilityId) {
        long approvedRosters = rosterRepository.countByTenantIdAndStatus(tenantId, "approved");
        long scheduledShifts = shiftRepository.countByTenantIdAndStatus(tenantId, "scheduled");
        long checkedInShifts = shiftRepository.countByTenantIdAndStatus(tenantId, "checked_in");
        long facilityScheduled = facilityId == null ? 0
                : shiftRepository.countByTenantIdAndFacilityIdAndStatus(tenantId, facilityId, "scheduled");
        return new VashandiDtos.RosterCoverageResponse(
                approvedRosters, scheduledShifts, checkedInShifts, facilityScheduled, "completed");
    }

    public VashandiDtos.AttendanceAnalyticsResponse attendance(UUID tenantId) {
        long checkIns = attendanceRepository.countByTenantIdAndEventType(tenantId, "check_in");
        long checkOuts = attendanceRepository.countByTenantIdAndEventType(tenantId, "check_out");
        long supervisorConfirmed = attendanceRepository.countByTenantIdAndEventType(tenantId, "supervisor_confirmed_check_in");
        return new VashandiDtos.AttendanceAnalyticsResponse(checkIns, checkOuts, supervisorConfirmed, "completed");
    }

    public VashandiDtos.VacancyAnalyticsResponse vacancies(UUID tenantId, UUID facilityId) {
        long activeAssignments = assignmentRepository.countByTenantIdAndStatus(tenantId, "active");
        long scheduledShifts = shiftRepository.countByTenantIdAndStatus(tenantId, "scheduled");
        long uncovered = Math.max(0, scheduledShifts - activeAssignments);
        if (facilityId != null) {
            long facilityScheduled = shiftRepository.countByTenantIdAndFacilityIdAndStatus(tenantId, facilityId, "scheduled");
            long facilityActive = assignmentRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, facilityId, "active").size();
            uncovered = Math.max(0, facilityScheduled - facilityActive);
        }
        return new VashandiDtos.VacancyAnalyticsResponse(uncovered, scheduledShifts, activeAssignments, "completed");
    }

    public VashandiDtos.AccessRiskAnalyticsResponse accessRisk(UUID tenantId) {
        long open = accessRiskRepository.countByTenantIdAndStatus(tenantId, "open");
        long critical = accessRiskRepository.countByTenantIdAndSeverity(tenantId, "critical");
        long high = accessRiskRepository.countByTenantIdAndSeverity(tenantId, "high");
        return new VashandiDtos.AccessRiskAnalyticsResponse(open, critical, high, "completed");
    }
}
