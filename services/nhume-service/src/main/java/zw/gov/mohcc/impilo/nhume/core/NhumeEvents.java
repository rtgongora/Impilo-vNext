package zw.gov.mohcc.impilo.nhume.core;

/**
 * Canonical Nhume event-type constants emitted through the outbox.
 *
 * <p>All Nhume events follow the {@code nhume.<aggregate>.<verb>.v1} pattern
 * so downstream consumers (Comms Hub, Trust Layer audit, analytics, NDR)
 * can subscribe deterministically.
 */
public final class NhumeEvents {

    private NhumeEvents() {}

    public static final String DELIVERY_REQUESTED      = "nhume.delivery.requested.v1";
    public static final String DELIVERY_SUBMITTED      = "nhume.delivery.submitted.v1";
    public static final String DELIVERY_VALIDATED      = "nhume.delivery.validated.v1";
    public static final String DELIVERY_APPROVED       = "nhume.delivery.approved.v1";
    public static final String DELIVERY_REJECTED       = "nhume.delivery.rejected.v1";
    public static final String DELIVERY_PAYMENT_REQUIRED = "nhume.delivery.payment_required.v1";
    public static final String DELIVERY_STOCK_CONFIRMED  = "nhume.delivery.stock_confirmed.v1";
    public static final String DELIVERY_ASSIGNED       = "nhume.delivery.assigned.v1";
    public static final String DELIVERY_ASSIGNMENT_ACCEPTED = "nhume.delivery.assignment_accepted.v1";
    public static final String DELIVERY_PICKUP_STARTED = "nhume.delivery.pickup_started.v1";
    public static final String DELIVERY_PICKED_UP      = "nhume.delivery.picked_up.v1";
    public static final String DELIVERY_IN_TRANSIT     = "nhume.delivery.in_transit.v1";
    public static final String DELIVERY_LOCATION_UPDATED = "nhume.delivery.location_updated.v1";
    public static final String DELIVERY_ARRIVED        = "nhume.delivery.arrived.v1";
    public static final String DELIVERY_ATTEMPTED      = "nhume.delivery.attempted.v1";
    public static final String DELIVERY_COMPLETED      = "nhume.delivery.completed.v1";
    public static final String DELIVERY_WRITEBACK      = "nhume.delivery.integration_writeback.v1";
    public static final String DELIVERY_FAILED         = "nhume.delivery.failed.v1";
    public static final String DELIVERY_RETURN_STARTED = "nhume.delivery.return_started.v1";
    public static final String DELIVERY_RETURN_COMPLETED = "nhume.delivery.return_completed.v1";
    public static final String DELIVERY_CANCELLED      = "nhume.delivery.cancelled.v1";
    public static final String DELIVERY_EXCEPTION      = "nhume.delivery.exception_raised.v1";
    public static final String DELIVERY_COC_UPDATED    = "nhume.delivery.chain_of_custody_updated.v1";
    public static final String DELIVERY_COLD_CHAIN_BREACH = "nhume.delivery.cold_chain_breach.v1";
    public static final String DELIVERY_ROUTE_DEVIATION = "nhume.delivery.route_deviation.v1";
    public static final String DELIVERY_PROOF_CAPTURED = "nhume.delivery.proof_captured.v1";

    // OF-B19 §12.6 — cold-chain IoT closed loop (excursions are unsuppressible).
    // Periodic TEMPERATURE_READINGs are custody-chain rows only (no outbox spam);
    // excursions always emit.
    public static final String DELIVERY_TEMPERATURE_EXCURSION = "nhume.delivery.temperature_excursion.v1";
    public static final String DELIVERY_STABILITY_DECISION   = "nhume.delivery.stability_decision.v1";

    // OF-B20 §12.9 — governed transport-mode decision at dispatch (journey #67).
    public static final String DELIVERY_MODE_ASSIGNED  = "nhume.delivery.mode_assigned.v1";
    public static final String DELIVERY_MODE_FALLBACK  = "nhume.delivery.mode_fallback.v1";

    public static final String FLEET_ASSET_LOCATION_UPDATED = "nhume.fleet.asset_location_updated.v1";

    public static final String AUTONOMOUS_MISSION_CREATED  = "nhume.autonomous.mission_created.v1";
    public static final String AUTONOMOUS_MISSION_STATUS   = "nhume.autonomous.mission_status_updated.v1";
    public static final String AUTONOMOUS_TELEMETRY        = "nhume.autonomous.telemetry_received.v1";
}
