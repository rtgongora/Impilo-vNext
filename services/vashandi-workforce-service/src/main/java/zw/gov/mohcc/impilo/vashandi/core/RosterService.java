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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RosterService.class);

    private final RosterRepository rosterRepository;
    private final ShiftRepository shiftRepository;
    private final VashandiOutboxWriter outboxWriter;
    private final zw.gov.mohcc.impilo.vashandi.integration.TusoIntegrationClient tusoIntegrationClient;
    private final boolean validateVirtualPools;

    public RosterService(RosterRepository rosterRepository,
                         ShiftRepository shiftRepository,
                         VashandiOutboxWriter outboxWriter,
                         zw.gov.mohcc.impilo.vashandi.integration.TusoIntegrationClient tusoIntegrationClient,
                         @org.springframework.beans.factory.annotation.Value(
                                 "${impilo.vashandi.integration.validate-virtual-pools:false}")
                         boolean validateVirtualPools) {
        this.rosterRepository = rosterRepository;
        this.shiftRepository = shiftRepository;
        this.outboxWriter = outboxWriter;
        this.tusoIntegrationClient = tusoIntegrationClient;
        this.validateVirtualPools = validateVirtualPools;
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
        applyVirtualPoolSoftValidation(shift);
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

    /**
     * Soft validation of a shift's virtual pool against TUSO's routing seams:
     * accept-and-flag, never block. When enabled and the pool is verifiably
     * unknown, the shift is stamped {@code audit_metadata_json.unknownPool=true}
     * so rosters against typo'd/retired pools are auditable. TUSO
     * unreachable/disabled → no flag (shift creation must never hard-depend on
     * TUSO availability).
     */
    private void applyVirtualPoolSoftValidation(ShiftEntity shift) {
        if (!validateVirtualPools || shift.getVirtualPoolId() == null || shift.getVirtualPoolId().isBlank()) {
            return;
        }
        try {
            tusoIntegrationClient.virtualPoolKnown(shift.getVirtualPoolId()).ifPresent(known -> {
                if (!known) {
                    shift.setAuditMetadataJson(mergeAuditFlag(shift.getAuditMetadataJson()));
                    log.warn("Shift for virtual pool '{}' does not match any known TUSO routing seam — "
                            + "accepted and flagged unknownPool=true", shift.getVirtualPoolId());
                }
            });
        } catch (Exception e) {
            // Absolute soft gate: any validation failure is swallowed (logged) —
            // duty rostering must keep working when TUSO is down.
            log.warn("Virtual-pool soft validation skipped for '{}': {}",
                    shift.getVirtualPoolId(), e.getMessage());
        }
    }

    private static String mergeAuditFlag(String existingJson) {
        String flag = "\"unknownPool\":true";
        if (existingJson == null || existingJson.isBlank() || "{}".equals(existingJson.trim())) {
            return "{" + flag + "}";
        }
        String trimmed = existingJson.trim();
        if (trimmed.endsWith("}") && !trimmed.contains("\"unknownPool\"")) {
            return trimmed.substring(0, trimmed.length() - 1) + "," + flag + "}";
        }
        return trimmed;
    }

    private String actorId() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (IllegalStateException ex) {
            return "system";
        }
    }
}
