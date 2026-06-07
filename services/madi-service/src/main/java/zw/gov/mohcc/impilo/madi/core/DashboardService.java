package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.BloodOrderStatus;
import zw.gov.mohcc.impilo.madi.domain.BloodUnitStatus;
import zw.gov.mohcc.impilo.madi.domain.DriveStatus;
import zw.gov.mohcc.impilo.madi.integration.SurveillanceIntegration;
import zw.gov.mohcc.impilo.madi.persistence.repository.*;

import zw.gov.mohcc.impilo.madi.persistence.entity.BloodOrderEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodUnitEntity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final SurveillanceIntegration surveillanceIntegration;

    public DashboardService(DonorProfileRepository donorProfileRepository,
                            DonationDriveRepository driveRepository,
                            BloodUnitRepository bloodUnitRepository,
                            BloodOrderRepository orderRepository,
                            BloodInventoryBalanceRepository balanceRepository,
                            HaemovigilanceCaseRepository caseRepository,
                            SurveillanceIntegration surveillanceIntegration) {
        this.donorProfileRepository = donorProfileRepository;
        this.driveRepository = driveRepository;
        this.bloodUnitRepository = bloodUnitRepository;
        this.orderRepository = orderRepository;
        this.balanceRepository = balanceRepository;
        this.caseRepository = caseRepository;
        this.surveillanceIntegration = surveillanceIntegration;
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
    public Map<String, Object> thirtyDayForecast(UUID tenantId, UUID facilityId) {
        SurveillanceIntegration.EpidemiologyTuning tuning =
                surveillanceIntegration.fetchBloodDemandTuning(facilityId);
        double epidemiologyMultiplier = tuning.multiplier();

        OffsetDateTime since = OffsetDateTime.now().minusDays(30);
        Map<String, ForecastAccumulator> buckets = new HashMap<>();

        List<BloodOrderEntity> orders = facilityId != null
                ? orderRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(tenantId, facilityId)
                : orderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        for (BloodOrderEntity order : orders) {
            if (order.getCreatedAt() == null || order.getCreatedAt().isBefore(since)) {
                continue;
            }
            String key = bucketKey(order.getBloodGroup(), order.getComponentType());
            ForecastAccumulator acc = buckets.computeIfAbsent(key, k -> new ForecastAccumulator());
            acc.bloodGroup = order.getBloodGroup();
            acc.componentType = order.getComponentType();
            acc.projectedDemand30d += order.getUnitsRequested() != null ? order.getUnitsRequested() : 1;
        }

        List<BloodUnitEntity> units = bloodUnitRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        for (BloodUnitEntity unit : units) {
            if (facilityId != null && !facilityId.equals(unit.getFacilityId())) {
                continue;
            }
            String key = bucketKey(unit.getBloodGroup(), unit.getComponentType());
            ForecastAccumulator acc = buckets.computeIfAbsent(key, k -> new ForecastAccumulator());
            acc.bloodGroup = unit.getBloodGroup();
            acc.componentType = unit.getComponentType();
            if (BloodUnitStatus.AVAILABLE.name().equals(unit.getStatus())) {
                acc.currentAvailable++;
            }
            if (unit.getCreatedAt() != null && !unit.getCreatedAt().isBefore(since)) {
                acc.projectedSupply30d++;
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ForecastAccumulator acc : buckets.values()) {
            int tunedDemand = (int) Math.ceil(acc.projectedDemand30d * epidemiologyMultiplier);
            int netGap = acc.currentAvailable + acc.projectedSupply30d - tunedDemand;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bloodGroup", acc.bloodGroup);
            row.put("componentType", acc.componentType);
            row.put("currentAvailable", acc.currentAvailable);
            row.put("baselineDemand30d", acc.projectedDemand30d);
            row.put("projectedDemand30d", tunedDemand);
            row.put("projectedSupply30d", acc.projectedSupply30d);
            row.put("epidemiologyMultiplier", epidemiologyMultiplier);
            row.put("netGap", netGap);
            row.put("risk", netGap < 0 ? (netGap <= -5 ? "HIGH" : "MEDIUM") : "LOW");
            rows.add(row);
        }
        rows.sort(Comparator
                .comparing((Map<String, Object> r) -> String.valueOf(r.get("risk")))
                .thenComparing(r -> String.valueOf(r.get("bloodGroup")))
                .thenComparing(r -> String.valueOf(r.get("componentType"))));

        Map<String, Object> forecast = new LinkedHashMap<>();
        forecast.put("horizonDays", 30);
        forecast.put("generatedAt", OffsetDateTime.now());
        forecast.put("facilityId", facilityId);
        forecast.put("epidemiologyMultiplier", epidemiologyMultiplier);
        forecast.put("epidemiologySignals", tuning.signals());
        forecast.put("rows", rows);
        return forecast;
    }

    private static String bucketKey(String bloodGroup, String componentType) {
        return (bloodGroup != null ? bloodGroup : "?") + "|" + (componentType != null ? componentType : "?");
    }

    private static final class ForecastAccumulator {
        String bloodGroup;
        String componentType;
        int currentAvailable;
        int projectedDemand30d;
        int projectedSupply30d;
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
