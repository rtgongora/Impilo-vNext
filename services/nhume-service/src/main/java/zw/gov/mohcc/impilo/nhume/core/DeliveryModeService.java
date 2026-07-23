package zw.gov.mohcc.impilo.nhume.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.nhume.domain.AutonomousMissionEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryPackageEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.ModeCorridorEntity;
import zw.gov.mohcc.impilo.nhume.repository.AutonomousMissionRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryPackageRepository;
import zw.gov.mohcc.impilo.nhume.repository.ModeCorridorRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * OF-B20 §12.9 — governed transport-mode decision, evaluated once at dispatch.
 *
 * <p><b>HONESTY LAW (CONFIG-ONLY — R67/OF-G21):</b> drone delivery has ZERO
 * operational evidence on this estate. This service exists so the capability is
 * governed, auditable, and the ROAD fallback is real — journey #67. It plans
 * missions; it NEVER flies them:
 *
 * <ul>
 *   <li>Every mission it creates has status {@code NOT_FLOWN}.</li>
 *   <li>No code path in this service (or anywhere in nhume) may set a mission
 *       to a flight-claim status — {@link AutonomousMissionEntity#setStatus}
 *       rejects them at the domain boundary.</li>
 *   <li>The gate is fail-closed: a corridor passes only when it is explicitly
 *       enabled AND every eligibility constraint is provably satisfied AND the
 *       weather status is explicitly CLEAR. Unknown weight, unknown distance,
 *       unknown weather → governed ROAD fallback with a coded reason.</li>
 * </ul>
 */
@Service
public class DeliveryModeService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryModeService.class);

    /** The only status this estate may honestly record for an autonomous mission plan. */
    public static final String MISSION_STATUS_NOT_FLOWN = "NOT_FLOWN";

    public static final String MODE_ROAD = "ROAD";
    public static final String MODE_DRONE = "DRONE";

    private final ModeCorridorRepository corridorRepo;
    private final AutonomousMissionRepository missionRepo;
    private final DeliveryPackageRepository packageRepo;

    public DeliveryModeService(ModeCorridorRepository corridorRepo,
                               AutonomousMissionRepository missionRepo,
                               DeliveryPackageRepository packageRepo) {
        this.corridorRepo = corridorRepo;
        this.missionRepo = missionRepo;
        this.packageRepo = packageRepo;
    }

    /**
     * @param mode              decided transport mode (ROAD or DRONE)
     * @param corridorCode      the corridor evaluated (null when none matched at all)
     * @param fallbackReason    coded reason when the decision fell back to ROAD (null on DRONE)
     * @param missionId         planned mission id (present for DRONE, and RETAINED for a
     *                          weather fallback per journey #67 — never claimed flown)
     * @param droneConsidered   true when at least one enabled DRONE corridor existed, i.e.
     *                          the fallback is a real mode-change event worth a custody row
     */
    public record ModeDecision(String mode, String corridorCode, String fallbackReason,
                               UUID missionId, boolean droneConsidered) {

        static ModeDecision road(String reason, String corridorCode, UUID missionId,
                                 boolean droneConsidered) {
            return new ModeDecision(MODE_ROAD, corridorCode, reason, missionId, droneConsidered);
        }
    }

    /**
     * Evaluate the §12.9 matrix for a delivery at dispatch time. Persists the
     * planned mission where a drone corridor was weather-gated or selected;
     * never mutates the delivery itself (the caller owns delivery truth and the
     * custody/outbox trail).
     */
    public ModeDecision decideAtDispatch(DeliveryRequestEntity d) {
        List<ModeCorridorEntity> corridors = corridorRepo
                .findByTenantIdAndModeAndEnabledTrueOrderByCorridorCodeAsc(d.getTenantId(), MODE_DRONE);
        if (corridors.isEmpty()) {
            return ModeDecision.road("NO_ENABLED_DRONE_CORRIDOR", null, null, false);
        }

        String lastIneligibleReason = null;
        String lastIneligibleCorridor = null;
        for (ModeCorridorEntity corridor : corridors) {
            String ineligible = eligibilityFailure(d, corridor);
            if (ineligible != null) {
                lastIneligibleReason = ineligible;
                lastIneligibleCorridor = corridor.getCorridorCode();
                continue;
            }
            // Constraints hold — the weather envelope decides (enforced in dispatch,
            // never pilot discretion, §12.9). Only an explicit CLEAR passes.
            if ("CLEAR".equalsIgnoreCase(corridor.getWeatherStatus())) {
                AutonomousMissionEntity mission = planMission(d, corridor, null);
                log.info("OF-B20 mode gate: delivery {} DRONE-eligible on corridor {} — mission {} "
                        + "planned NOT_FLOWN (no flight claim; capability is config-only)",
                        d.getDeliveryId(), corridor.getCorridorCode(), mission.getMissionId());
                return new ModeDecision(MODE_DRONE, corridor.getCorridorCode(), null,
                        mission.getMissionId(), true);
            }
            // Journey #67 — governed fallback: the mission is RETAINED with its
            // fallback reason so the audit trail shows what was planned and why
            // it never flew; the delivery proceeds by ROAD, custody unbroken.
            String reason = "WEATHER_GATE_FAILED:" + corridor.getWeatherStatus().toUpperCase(java.util.Locale.ROOT);
            AutonomousMissionEntity mission = planMission(d, corridor, reason);
            log.info("OF-B20 mode gate: delivery {} corridor {} weather-gated ({}) — governed "
                    + "fallback to ROAD, mission {} retained NOT_FLOWN",
                    d.getDeliveryId(), corridor.getCorridorCode(), reason, mission.getMissionId());
            return ModeDecision.road(reason, corridor.getCorridorCode(), mission.getMissionId(), true);
        }
        return ModeDecision.road(lastIneligibleReason, lastIneligibleCorridor, null, true);
    }

    /** Coded eligibility failure, or null when every constraint provably holds (fail-closed). */
    private String eligibilityFailure(DeliveryRequestEntity d, ModeCorridorEntity corridor) {
        // Cargo class: controlled and cold-chain need explicit corridor certification;
        // hazardous / biohazard cargo never rides the drone lane on this estate.
        if (d.isControlledItem() && !corridor.isControlledAllowed()) {
            return "CARGO_CLASS_BLOCKED:CONTROLLED";
        }
        if (d.isColdChainRequired() && !corridor.isColdChainAllowed()) {
            return "CARGO_CLASS_BLOCKED:COLD_CHAIN_UNCERTIFIED";
        }
        if (d.isHazardous() || d.isBiohazard()) {
            return "CARGO_CLASS_BLOCKED:HAZARDOUS";
        }
        // Payload mass: unknown weight cannot pass a mass-limited corridor.
        if (corridor.getMaxPayloadGrams() != null) {
            BigDecimal weight = totalPayloadGrams(d.getDeliveryId());
            if (weight == null) {
                return "WEIGHT_UNKNOWN";
            }
            if (weight.compareTo(corridor.getMaxPayloadGrams()) > 0) {
                return "WEIGHT_EXCEEDED:" + weight.toPlainString() + "g>"
                        + corridor.getMaxPayloadGrams().toPlainString() + "g";
            }
        }
        // Range: unknown coordinates cannot pass a range-limited corridor.
        if (corridor.getMaxDistanceKm() != null) {
            Double km = distanceKm(d);
            if (km == null) {
                return "DISTANCE_UNKNOWN";
            }
            if (km > corridor.getMaxDistanceKm().doubleValue()) {
                return "DISTANCE_EXCEEDED:" + String.format(java.util.Locale.ROOT, "%.1f", km)
                        + "km>" + corridor.getMaxDistanceKm().toPlainString() + "km";
            }
        }
        return null;
    }

    private AutonomousMissionEntity planMission(DeliveryRequestEntity d, ModeCorridorEntity corridor,
                                                String fallbackReason) {
        AutonomousMissionEntity m = new AutonomousMissionEntity();
        m.setMissionId(UUID.randomUUID());
        m.setTenantId(d.getTenantId());
        m.setDeliveryId(d.getDeliveryId());
        m.setMissionKind(MODE_DRONE);
        m.setStatus(MISSION_STATUS_NOT_FLOWN);
        m.setCorridorCode(corridor.getCorridorCode());
        m.setFallbackReason(fallbackReason);
        m.setLaunchSite(d.getOriginLabel());
        m.setLandingSite(d.getDestinationLabel());
        m.setSimulationMode(true); // honest: nothing on this estate ever flies
        return missionRepo.save(m);
    }

    /** Sum of recorded package weights; null when no package carries a weight (fail-closed). */
    private BigDecimal totalPayloadGrams(UUID deliveryId) {
        List<DeliveryPackageEntity> packages = packageRepo.findByDeliveryId(deliveryId);
        BigDecimal total = null;
        for (DeliveryPackageEntity p : packages) {
            if (p.getWeightGrams() == null) {
                return null; // any unweighed package makes total mass unprovable
            }
            total = total == null ? p.getWeightGrams() : total.add(p.getWeightGrams());
        }
        return total;
    }

    /** Great-circle origin→destination distance; null when either endpoint lacks coordinates. */
    private Double distanceKm(DeliveryRequestEntity d) {
        if (d.getOriginLat() == null || d.getOriginLng() == null
                || d.getDestinationLat() == null || d.getDestinationLng() == null) {
            return null;
        }
        double lat1 = Math.toRadians(d.getOriginLat());
        double lat2 = Math.toRadians(d.getDestinationLat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(d.getDestinationLng() - d.getOriginLng());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
