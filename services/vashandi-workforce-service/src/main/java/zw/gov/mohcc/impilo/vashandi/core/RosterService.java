package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.RosterEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.RosterRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RosterService {

    private final RosterRepository rosterRepository;
    private final ShiftRepository shiftRepository;
    private final VashandiOutboxWriter outboxWriter;

    public RosterService(RosterRepository rosterRepository,
                         ShiftRepository shiftRepository,
                         VashandiOutboxWriter outboxWriter) {
        this.rosterRepository = rosterRepository;
        this.shiftRepository = shiftRepository;
        this.outboxWriter = outboxWriter;
    }

    public List<RosterEntity> list(UUID tenantId) {
        return rosterRepository.findByTenantIdOrderByPeriodStartDesc(tenantId);
    }

    public Optional<RosterEntity> get(UUID tenantId, UUID id) {
        return rosterRepository.findByTenantIdAndId(tenantId, id);
    }

    public List<ShiftEntity> shifts(UUID tenantId, UUID rosterId) {
        return shiftRepository.findByTenantIdAndRosterIdOrderByStartTimeAsc(tenantId, rosterId);
    }

    @Transactional
    public RosterEntity create(UUID tenantId, VashandiDtos.CreateRosterRequest request) throws Exception {
        RosterEntity roster = new RosterEntity();
        roster.setTenantId(tenantId);
        roster.setOrganisationId(request.organisationId());
        roster.setFacilityId(request.facilityId());
        roster.setDepartmentId(request.departmentId());
        roster.setUnitId(request.unitId());
        roster.setRosterType(request.rosterType());
        roster.setPeriodStart(request.periodStart());
        roster.setPeriodEnd(request.periodEnd());
        roster.setCreatedBy(actorId());
        RosterEntity saved = rosterRepository.save(roster);

        Map<String, Object> payload = Map.of("rosterId", saved.getId().toString());
        outboxWriter.publish(tenantId, "ROSTER", saved.getId().toString(), "roster", "created",
                "vashandi:roster:created:" + saved.getId(), payload);
        return saved;
    }

    @Transactional
    public RosterEntity approve(UUID tenantId, UUID rosterId) throws Exception {
        RosterEntity roster = rosterRepository.findByTenantIdAndId(tenantId, rosterId)
                .orElseThrow(() -> new IllegalArgumentException("roster not found"));
        roster.setStatus("approved");
        roster.setApprovedBy(actorId());
        RosterEntity saved = rosterRepository.save(roster);
        outboxWriter.publish(tenantId, "ROSTER", saved.getId().toString(), "roster", "approved",
                "vashandi:roster:approved:" + saved.getId(), Map.of("rosterId", saved.getId().toString()));
        return saved;
    }

    @Transactional
    public ShiftEntity createShift(UUID tenantId, VashandiDtos.CreateShiftRequest request) throws Exception {
        rosterRepository.findByTenantIdAndId(tenantId, request.rosterId())
                .orElseThrow(() -> new IllegalArgumentException("roster not found"));
        ShiftEntity shift = new ShiftEntity();
        shift.setTenantId(tenantId);
        shift.setRosterId(request.rosterId());
        shift.setWorkforceProfileId(request.workforceProfileId());
        shift.setAssignmentId(request.assignmentId());
        shift.setShiftType(request.shiftType());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        shift.setLocationType(request.locationType());
        shift.setFacilityId(request.facilityId());
        shift.setVirtualPoolId(request.virtualPoolId());
        shift.setCheckInRequired(request.checkInRequired() == null || request.checkInRequired());
        ShiftEntity saved = shiftRepository.save(shift);
        outboxWriter.publish(tenantId, "SHIFT", saved.getId().toString(), "shift", "created",
                "vashandi:shift:created:" + saved.getId(), Map.of("shiftId", saved.getId().toString()));
        return saved;
    }

    @Transactional
    public ShiftEntity updateShift(UUID tenantId, UUID shiftId, VashandiDtos.UpdateShiftRequest request) throws Exception {
        ShiftEntity shift = shiftRepository.findByTenantIdAndId(tenantId, shiftId)
                .orElseThrow(() -> new IllegalArgumentException("shift not found"));
        if (request.status() != null) {
            shift.setStatus(request.status());
        }
        if (request.startTime() != null) {
            shift.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            shift.setEndTime(request.endTime());
        }
        ShiftEntity saved = shiftRepository.save(shift);
        outboxWriter.publish(tenantId, "SHIFT", saved.getId().toString(), "shift", "updated",
                "vashandi:shift:updated:" + saved.getId() + ":" + System.currentTimeMillis(),
                Map.of("shiftId", saved.getId().toString(), "status", saved.getStatus()));
        return saved;
    }

    private String actorId() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (IllegalStateException ex) {
            return "system";
        }
    }
}
