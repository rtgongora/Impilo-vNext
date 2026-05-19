package zw.gov.mohcc.impilo.nhume.domain;

/**
 * Recognised transport modes for Nhume deliveries.
 *
 * <p>The list is intentionally broad: Nhume must not be hard-coded to
 * cars and motorbikes. Autonomous modes are first-class citizens.
 */
public enum DeliveryMode {
    WALKING_COURIER,
    BICYCLE,
    MOTORCYCLE,
    CAR,
    VAN,
    TRUCK,
    AMBULANCE,
    BOAT,
    DRONE,
    AUTONOMOUS_VEHICLE,
    ROBOT,
    SMART_LOCKER,
    PARTNER_COURIER,
    THIRD_PARTY_FLEET,
    PUBLIC_SECTOR_VEHICLE,
    COMMUNITY_HEALTH_WORKER,
    FACILITY_ARRANGED,
    OTHER
}
