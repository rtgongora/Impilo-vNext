package zw.gov.mohcc.impilo.nhume.integration.autonomous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Built-in simulation adapter for autonomous providers. Returns deterministic,
 * clearly-labelled SIMULATION telemetry so demos and tests look realistic
 * without ever attempting to launch a real drone, robot or locker action.
 */
@Component
public class SimulatedAutonomousAdapter implements AutonomousDeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedAutonomousAdapter.class);

    @Value("${impilo.nhume.autonomous.simulation-enabled:true}")
    private boolean enabled;

    @Override
    public String providerKind() {
        return "SIMULATION";
    }

    @Override
    public boolean supports(String providerCode) {
        // The simulated adapter is a fall-through: it handles any provider
        // whose code begins with "sim-" or when simulation is enabled.
        return enabled && providerCode != null
                && (providerCode.startsWith("sim-") || providerCode.contains("simulation"));
    }

    @Override
    public MissionAck createMission(MissionRequest request) {
        log.info("[autonomous-sim] create mission provider={} delivery={} launch={} land={}",
                request.providerCode(), request.deliveryId(),
                request.launchSite(), request.landingSite());
        Map<String, Object> telemetry = baseTelemetry(request.providerCode());
        telemetry.put("simulation", true);
        return new MissionAck(
                "sim-mission-" + UUID.randomUUID(),
                "ACCEPTED",
                "Simulation mission accepted (not a real launch)",
                telemetry);
    }

    @Override
    public MissionStatus getMissionStatus(String missionRef) {
        Map<String, Object> telemetry = baseTelemetry("simulation");
        telemetry.put("simulation", true);
        telemetry.put("status_polled", true);
        return new MissionStatus(missionRef, "EN_ROUTE", OffsetDateTime.now(), telemetry);
    }

    @Override
    public MissionAck cancelMission(String missionRef, String reason) {
        log.info("[autonomous-sim] cancel mission {} reason={}", missionRef, reason);
        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("simulation", true);
        telemetry.put("cancel_reason", reason);
        return new MissionAck(missionRef, "CANCELLED",
                "Simulation mission cancelled", telemetry);
    }

    private Map<String, Object> baseTelemetry(String providerCode) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("provider_code", providerCode);
        t.put("provider_kind", "SIMULATION");
        t.put("issued_at", OffsetDateTime.now().toString());
        t.put("autonomy_level", "SIMULATED");
        t.put("battery_pct", 85);
        t.put("payload_locked", true);
        return t;
    }
}
