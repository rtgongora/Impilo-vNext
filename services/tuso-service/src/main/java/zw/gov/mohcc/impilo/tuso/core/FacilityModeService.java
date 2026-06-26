package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityModeContextResponse;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySetupStateEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.OccupancySnapshotEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.AlertRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.OccupancySnapshotRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;

import java.util.List;

/**
 * Produces the C4 {@code FacilityModeContext} read-model (single producer).
 *
 * <p>Assembles the facility node, setup-wizard state, and a scoped operational
 * snapshot. Tenant-scoped; the {@code visibility} flag (ROW_DETAIL / AGGREGATE_ONLY)
 * is resolved by the caller from the cross-tenant visibility guard and threaded in.</p>
 */
@Service
public class FacilityModeService {

    private static final Logger log = LoggerFactory.getLogger(FacilityModeService.class);

    private final FacilityRepository facilityRepository;
    private final FacilityUnitRepository facilityUnitRepository;
    private final ServicePointRepository servicePointRepository;
    private final OccupancySnapshotRepository occupancyRepository;
    private final AlertRepository alertRepository;
    private final FacilitySetupService setupService;

    public FacilityModeService(FacilityRepository facilityRepository,
                               FacilityUnitRepository facilityUnitRepository,
                               ServicePointRepository servicePointRepository,
                               OccupancySnapshotRepository occupancyRepository,
                               AlertRepository alertRepository,
                               FacilitySetupService setupService) {
        this.facilityRepository = facilityRepository;
        this.facilityUnitRepository = facilityUnitRepository;
        this.servicePointRepository = servicePointRepository;
        this.occupancyRepository = occupancyRepository;
        this.alertRepository = alertRepository;
        this.setupService = setupService;
    }

    @Transactional
    public FacilityModeContextResponse buildContext(TrustContext ctx, Long facilityId, String visibility) {
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        boolean aggregateOnly = "AGGREGATE_ONLY".equals(visibility);

        List<FacilityUnitEntity> units = facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(facilityId);
        List<FacilityModeContextResponse.DepartmentRef> departments = units.stream()
                .map(u -> new FacilityModeContextResponse.DepartmentRef(
                        u.getId(), u.getName(), u.getServiceLine()))
                .toList();

        List<ServicePointEntity> sps = servicePointRepository.findByFacilityIdAndActiveTrueOrderByCreatedAtAsc(facilityId);
        List<FacilityModeContextResponse.ServicePointRef> servicePoints = sps.stream()
                .map(sp -> new FacilityModeContextResponse.ServicePointRef(
                        sp.getId().toString(), sp.getFacilityUnitId(), sp.getName(), sp.getQueueId()))
                .toList();

        FacilityModeContextResponse.FacilityNode node = new FacilityModeContextResponse.FacilityNode(
                facility.getId(),
                facility.getTenantId() != null ? facility.getTenantId().toString() : null,
                facility.getFacilityCode(),
                facility.getName(),
                facility.getFacilityType(),
                facility.getRegulatoryStatus() != null ? facility.getRegulatoryStatus().name() : null,
                // PII-free structural refs are safe under aggregate-only.
                departments,
                servicePoints);

        FacilitySetupStateEntity s = setupService.getOrCreateState(ctx, facilityId);
        FacilityModeContextResponse.SetupState setupState = new FacilityModeContextResponse.SetupState(
                s.isDepartmentsConfigured(),
                s.isServicePointsConfigured(),
                s.isQueuesConfigured(),
                s.isWorkflowsConfigured(),
                s.isWorkforceLinked(),
                s.isOrosRoutingConfigured(),
                s.isKhulumaChannelsConfigured(),
                s.isFundoReady(),
                s.isGoLive());

        FacilityModeContextResponse.Ops ops = buildOps(facilityId);

        return new FacilityModeContextResponse(
                node, setupState, ops, aggregateOnly ? "AGGREGATE_ONLY" : "ROW_DETAIL");
    }

    /**
     * Operational snapshot derived from the control-tower occupancy + alert data.
     * Counts are aggregate (non-PII) and therefore safe under aggregate-only visibility.
     */
    private FacilityModeContextResponse.Ops buildOps(Long facilityId) {
        OccupancySnapshotEntity occ = occupancyRepository.findLatestByFacility(facilityId);
        Integer bedOccupancy = null;
        if (occ != null && occ.getTotalBeds() != null && occ.getTotalBeds() > 0) {
            bedOccupancy = (int) Math.round(
                    100.0 * occ.getOccupiedBeds() / occ.getTotalBeds());
        }
        int openAlerts = (int) alertRepository.findByFacilityIdAndStatusOrderByCreatedAtDesc(
                facilityId, "OPEN", PageRequest.of(0, 1)).getTotalElements();
        // queueDepth/openEncounters are sourced from PCT telemetry; the BFF composes the
        // live PCT queue snapshot. TUSO surfaces what it owns: open alerts as a proxy of
        // operational load plus persisted occupancy. Live encounter/queue counts are
        // overlaid by the BFF FacilityModeController from PctServiceClient.
        return new FacilityModeContextResponse.Ops(0, openAlerts, bedOccupancy);
    }
}
