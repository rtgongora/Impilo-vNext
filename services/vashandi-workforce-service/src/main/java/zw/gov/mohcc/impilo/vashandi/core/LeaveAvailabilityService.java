package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.LeaveAvailabilityEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.LeaveAvailabilityRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class LeaveAvailabilityService {

    private final LeaveAvailabilityRepository leaveRepository;
    private final VashandiOutboxWriter outboxWriter;

    public LeaveAvailabilityService(LeaveAvailabilityRepository leaveRepository,
                                    VashandiOutboxWriter outboxWriter) {
        this.leaveRepository = leaveRepository;
        this.outboxWriter = outboxWriter;
    }

    public List<LeaveAvailabilityEntity> list(UUID tenantId, UUID workforceProfileId) {
        if (workforceProfileId != null) {
            return leaveRepository.findByTenantIdAndWorkforceProfileIdOrderByStartDateDesc(tenantId, workforceProfileId);
        }
        return leaveRepository.findByTenantIdOrderByStartDateDesc(tenantId);
    }

    public Optional<LeaveAvailabilityEntity> get(UUID tenantId, UUID id) {
        return leaveRepository.findByTenantIdAndId(tenantId, id);
    }

    @Transactional
    public LeaveAvailabilityEntity create(UUID tenantId, VashandiDtos.CreateLeaveRequest request) throws Exception {
        LeaveAvailabilityEntity leave = new LeaveAvailabilityEntity();
        leave.setTenantId(tenantId);
        leave.setWorkforceProfileId(request.workforceProfileId());
        leave.setLeaveType(request.leaveType());
        leave.setStatus(request.status() != null ? request.status() : "pending");
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setSourceAuthority(request.sourceAuthority());
        LeaveAvailabilityEntity saved = leaveRepository.save(leave);
        emit(tenantId, saved, "created");
        return saved;
    }

    @Transactional
    public LeaveAvailabilityEntity update(UUID tenantId, UUID id, VashandiDtos.UpdateLeaveRequest request)
            throws Exception {
        LeaveAvailabilityEntity leave = leaveRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("leave record not found"));
        if (request.status() != null) {
            leave.setStatus(request.status());
        }
        if (request.endDate() != null) {
            leave.setEndDate(request.endDate());
        }
        if (request.approvedBy() != null) {
            leave.setApprovedBy(request.approvedBy());
        } else if ("approved".equals(request.status())) {
            leave.setApprovedBy(actorId());
        }
        LeaveAvailabilityEntity saved = leaveRepository.save(leave);
        emit(tenantId, saved, "updated");
        return saved;
    }

    private void emit(UUID tenantId, LeaveAvailabilityEntity leave, String action) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("leaveId", leave.getId().toString());
        payload.put("status", leave.getStatus());
        outboxWriter.publish(tenantId, "LEAVE", leave.getId().toString(), "leave", action,
                "vashandi:leave:" + action + ":" + leave.getId(), payload);
    }

    private String actorId() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (IllegalStateException ex) {
            return "system";
        }
    }
}
