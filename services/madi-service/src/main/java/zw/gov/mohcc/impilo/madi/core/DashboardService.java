package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.BloodOrderStatus;
import zw.gov.mohcc.impilo.madi.domain.BloodUnitStatus;
import zw.gov.mohcc.impilo.madi.domain.DriveStatus;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {

    private final DonorProfileRepository donorProfileRepository;
    private final DonationDriveRepository driveRepository;
    private final BloodUnitRepository bloodUnitRepository;
    private final BloodOrderRepository orderRepository;
    private final BloodInventoryBalanceRepository balanceRepository;
    private final HaemovigilanceCaseRepository caseRepository;

    public DashboardService(DonorProfileRepository donorProfileRepository,
                            DonationDriveRepository driveRepository,
                            BloodUnitRepository bloodUnitRepository,
                            BloodOrderRepository orderRepository,
                            BloodInventoryBalanceRepository balanceRepository,
                            HaemovigilanceCaseRepository caseRepository) {
        this.donorProfileRepository = donorProfileRepository;
        this.driveRepository = driveRepository;
        this.bloodUnitRepository = bloodUnitRepository;
        this.orderRepository = orderRepository;
        this.balanceRepository = balanceRepository;
        this.caseRepository = caseRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> facilityMetrics(UUID tenantId, UUID facilityId) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("scope", "facility");
        metrics.put("facilityId", facilityId);
        metrics.put("donors", donorProfileRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).size());
        metrics.put("openOrders", orderRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(tenantId, facilityId)
                .stream().filter(o -> !BloodOrderStatus.COMPLETED.name().equals(o.getStatus())
                        && !BloodOrderStatus.CANCELLED.name().equals(o.getStatus())).count());
        metrics.put("availableUnits", bloodUnitRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream().filter(u -> BloodUnitStatus.AVAILABLE.name().equals(u.getStatus())
                        && facilityId.equals(u.getFacilityId())).count());
        return metrics;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> localMetrics(UUID tenantId) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("scope", "local");
        metrics.put("donors", donorProfileRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).size());
        metrics.put("activeDrives", driveRepository.findByTenantIdAndStatusOrderByStartAtAsc(tenantId, DriveStatus.OPEN.name()).size());
        metrics.put("pendingOrders", orderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream().filter(o -> BloodOrderStatus.SUBMITTED.name().equals(o.getStatus())
                        || BloodOrderStatus.CROSSMATCH_PENDING.name().equals(o.getStatus())).count());
        return metrics;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> centralMetrics(UUID tenantId) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("scope", "central");
        metrics.put("totalUnits", bloodUnitRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).size());
        metrics.put("availableUnits", bloodUnitRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream().filter(u -> BloodUnitStatus.AVAILABLE.name().equals(u.getStatus())).count());
        metrics.put("openHaemovigilanceCases", caseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream().filter(c -> "OPEN".equals(c.getStatus()) || "INVESTIGATING".equals(c.getStatus())).count());
        return metrics;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> driveMetrics(UUID tenantId, UUID driveId) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("scope", "drive");
        metrics.put("driveId", driveId);
        driveRepository.findByDriveIdAndTenantId(driveId, tenantId).ifPresent(d -> {
            metrics.put("title", d.getTitle());
            metrics.put("status", d.getStatus());
            metrics.put("capacity", d.getCapacity());
        });
        return metrics;
    }
}
