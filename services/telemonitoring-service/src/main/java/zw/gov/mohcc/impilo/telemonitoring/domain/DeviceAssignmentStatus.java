package zw.gov.mohcc.impilo.telemonitoring.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Clinical device-assignment lifecycle (OF-B24, Vol II §15.5/§15.6) — the CLINICAL leg of
 * the three-way device split. This machine governs the patient↔device↔plan binding, not
 * the device's registry state (iot-ingestion) or its physical state (asset-registry).
 *
 * <pre>
 * ASSIGNED -> ACTIVE (training recorded, §15.6) -> SUSPENDED -> ACTIVE
 * ASSIGNED/ACTIVE/SUSPENDED -> RETURNED | LOST
 * RETURNED / LOST are terminal — history is never deleted; readings keep their
 * assignment-era provenance forever (§15.6 rules).
 * </pre>
 */
public enum DeviceAssignmentStatus {
    ASSIGNED,
    ACTIVE,
    SUSPENDED,
    RETURNED,
    LOST;

    private static final Map<DeviceAssignmentStatus, Set<DeviceAssignmentStatus>> ALLOWED = Map.of(
            ASSIGNED, EnumSet.of(ACTIVE, RETURNED, LOST),
            ACTIVE, EnumSet.of(SUSPENDED, RETURNED, LOST),
            SUSPENDED, EnumSet.of(ACTIVE, RETURNED, LOST),
            RETURNED, EnumSet.noneOf(DeviceAssignmentStatus.class),
            LOST, EnumSet.noneOf(DeviceAssignmentStatus.class));

    /** True when the machine permits {@code from -> to}. */
    public static boolean canTransition(DeviceAssignmentStatus from, DeviceAssignmentStatus to) {
        return from != null && to != null && ALLOWED.get(from).contains(to);
    }

    /** True when the assignment is closed: the device may serve another patient. */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
