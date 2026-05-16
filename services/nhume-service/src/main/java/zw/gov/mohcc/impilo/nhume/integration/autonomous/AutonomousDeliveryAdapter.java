package zw.gov.mohcc.impilo.nhume.integration.autonomous;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Adapter contract for autonomous delivery providers: drones/UAVs,
 * autonomous ground vehicles, robots, and smart-locker networks.
 *
 * <p>Implementations must remain integration-shaped: never simulate a real
 * flight. The bundled {@code SimulatedAutonomousAdapter} produces clearly
 * labelled simulation telemetry for development and demos.
 */
public interface AutonomousDeliveryAdapter {

    /** Provider classification — DRONE, ROBOT, SMART_LOCKER, AGV, ... */
    String providerKind();

    /** Returns true if this adapter handles the supplied provider code. */
    boolean supports(String providerCode);

    MissionAck createMission(MissionRequest request);

    MissionStatus getMissionStatus(String missionRef);

    MissionAck cancelMission(String missionRef, String reason);

    record MissionRequest(
            String providerCode,
            String deliveryId,
            String launchSite,
            String landingSite,
            OffsetDateTime scheduledFor,
            Map<String, Object> payloadMetadata
    ) {}

    record MissionAck(
            String missionRef,
            String status,
            String message,
            Map<String, Object> telemetry
    ) {}

    record MissionStatus(
            String missionRef,
            String status,
            OffsetDateTime statusAt,
            Map<String, Object> telemetry
    ) {}
}
