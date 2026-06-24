package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.LeaveAvailabilityEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.LeaveBalanceEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.LeaveAvailabilityRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.LeaveBalanceRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class LeaveAvailabilityService {

    private final LeaveAvailabilityRepository leaveRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final VashandiOutboxWriter outboxWriter;

    public LeaveAvailabilityService(LeaveAvailabilityRepository leaveRepository,
                                    LeaveBalanceRepository balanceRepository,
                                    VashandiOutboxWriter outboxWriter) {
        this.leaveRepository = leaveRepository;
        this.balanceRepository = balanceRepository;
        this.outboxWriter = outboxWriter;
    }

    public List<LeaveBalanceEntity> balances(UUID tenantId, UUID workforceProfileId, int fiscalYear) {
        return balanceRepository.findByTenantIdAndWorkforceProfileIdAndFiscalYear(
                tenantId, workforceProfileId, fiscalYear);
    }

    @Transactional
    public LeaveBalanceEntity upsertBalance(UUID tenantId, VashandiDtos.UpsertLeaveBalanceRequest request) {
        LeaveBalanceEntity balance = balanceRepository
                .findByTenantIdAndWorkforceProfileIdAndLeaveTypeAndFiscalYear(
                        tenantId, request.workforceProfileId(), request.leaveType(), request.fiscalYear())
                .orElseGet(() -> {
                    LeaveBalanceEntity b = new LeaveBalanceEntity();
                    b.setTenantId(tenantId);
                    b.setWorkforceProfileId(request.workforceProfileId());
                    b.setLeaveType(request.leaveType());
                    b.setFiscalYear(request.fiscalYear());
                    return b;
                });
        balance.setEntitlement(request.entitlement());
        balance.setCarriedOver(request.carriedOver());
        balance.setUsed(request.used());
        balance.setAvailable(request.entitlement() + request.carriedOver() - request.used());
        return balanceRepository.save(balance);
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

        // Reserve against entitlement IF a balance is configured for this (profile, type, year).
        // When no balance row exists, the leave window is recorded without enforcement so
        // existing operational use is not broken.
        if (request.startDate() != null && request.endDate() != null) {
            int fiscalYear = request.startDate().getYear();
            int days = (int) (ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1);
            Optional<LeaveBalanceEntity> balanceOpt = balanceRepository
                    .findByTenantIdAndWorkforceProfileIdAndLeaveTypeAndFiscalYear(
                            tenantId, request.workforceProfileId(), request.leaveType(), fiscalYear);
            if (balanceOpt.isPresent() && days > 0) {
                LeaveBalanceEntity balance = balanceOpt.get();
                if (balance.getAvailable() < days) {
                    throw new IllegalArgumentException(
                            "Insufficient leave balance: available=" + balance.getAvailable() + ", requested=" + days);
                }
                balance.setUsed(balance.getUsed() + days);
                balance.setAvailable(balance.getAvailable() - days);
                balanceRepository.save(balance);
            }
        }

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
