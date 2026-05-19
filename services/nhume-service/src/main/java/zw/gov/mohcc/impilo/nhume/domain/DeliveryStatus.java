package zw.gov.mohcc.impilo.nhume.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Canonical Nhume delivery lifecycle statuses.
 *
 * <p>The state machine intentionally accepts most reasonable transitions
 * (including jumps used for emergencies or manual corrections); transitions
 * that are clearly nonsensical (e.g. CLOSED → DRAFT) are rejected by
 * {@link #canTransition(DeliveryStatus, DeliveryStatus)}.
 */
public enum DeliveryStatus {
    DRAFT,
    SUBMITTED,
    VALIDATION_REQUIRED,
    AWAITING_APPROVAL,
    APPROVED,
    REJECTED,
    AWAITING_PAYMENT,
    AWAITING_STOCK,
    AWAITING_PICKUP,
    DISPATCH_PENDING,
    ASSIGNED,
    ACCEPTED,
    EN_ROUTE_TO_PICKUP,
    PICKED_UP,
    IN_TRANSIT,
    AT_DESTINATION,
    DELIVERY_ATTEMPTED,
    DELIVERED,
    PARTIALLY_DELIVERED,
    FAILED,
    RETURNED,
    CANCELLED,
    DISPUTED,
    CLOSED;

    /** Terminal statuses cannot be left except via re-open/dispute flows handled explicitly. */
    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED || this == RETURNED
                || this == CANCELLED || this == CLOSED || this == REJECTED;
    }

    /** Statuses considered actively "open" (visible in dispatcher queues). */
    public boolean isOpen() {
        return !isTerminal();
    }

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED = Map.ofEntries(
            Map.entry(DRAFT, EnumSet.of(SUBMITTED, CANCELLED)),
            Map.entry(SUBMITTED, EnumSet.of(VALIDATION_REQUIRED, AWAITING_APPROVAL, APPROVED,
                    AWAITING_PAYMENT, AWAITING_STOCK, REJECTED, CANCELLED, DISPATCH_PENDING, ASSIGNED)),
            Map.entry(VALIDATION_REQUIRED, EnumSet.of(AWAITING_APPROVAL, APPROVED, REJECTED, CANCELLED)),
            Map.entry(AWAITING_APPROVAL, EnumSet.of(APPROVED, REJECTED, CANCELLED)),
            Map.entry(APPROVED, EnumSet.of(AWAITING_PAYMENT, AWAITING_STOCK, DISPATCH_PENDING,
                    ASSIGNED, CANCELLED)),
            Map.entry(AWAITING_PAYMENT, EnumSet.of(AWAITING_STOCK, DISPATCH_PENDING, ASSIGNED, CANCELLED)),
            Map.entry(AWAITING_STOCK, EnumSet.of(DISPATCH_PENDING, AWAITING_PICKUP, ASSIGNED, CANCELLED)),
            Map.entry(AWAITING_PICKUP, EnumSet.of(ASSIGNED, DISPATCH_PENDING, CANCELLED)),
            Map.entry(DISPATCH_PENDING, EnumSet.of(ASSIGNED, CANCELLED)),
            Map.entry(ASSIGNED, EnumSet.of(ACCEPTED, EN_ROUTE_TO_PICKUP, PICKED_UP,
                    DISPATCH_PENDING, ASSIGNED, CANCELLED, FAILED)),
            Map.entry(ACCEPTED, EnumSet.of(EN_ROUTE_TO_PICKUP, PICKED_UP, FAILED, CANCELLED)),
            Map.entry(EN_ROUTE_TO_PICKUP, EnumSet.of(PICKED_UP, FAILED, CANCELLED)),
            Map.entry(PICKED_UP, EnumSet.of(IN_TRANSIT, AT_DESTINATION, FAILED, CANCELLED)),
            Map.entry(IN_TRANSIT, EnumSet.of(AT_DESTINATION, DELIVERY_ATTEMPTED, DELIVERED,
                    PARTIALLY_DELIVERED, FAILED, CANCELLED, RETURNED)),
            Map.entry(AT_DESTINATION, EnumSet.of(DELIVERY_ATTEMPTED, DELIVERED,
                    PARTIALLY_DELIVERED, FAILED, RETURNED)),
            Map.entry(DELIVERY_ATTEMPTED, EnumSet.of(DELIVERED, PARTIALLY_DELIVERED,
                    FAILED, RETURNED, IN_TRANSIT)),
            Map.entry(DELIVERED, EnumSet.of(DISPUTED, CLOSED, RETURNED)),
            Map.entry(PARTIALLY_DELIVERED, EnumSet.of(DELIVERED, FAILED, RETURNED, CLOSED, DISPUTED)),
            Map.entry(FAILED, EnumSet.of(RETURNED, CLOSED, DISPUTED, ASSIGNED)),
            Map.entry(RETURNED, EnumSet.of(CLOSED, DISPUTED)),
            Map.entry(CANCELLED, EnumSet.of(CLOSED)),
            Map.entry(REJECTED, EnumSet.of(CLOSED)),
            Map.entry(DISPUTED, EnumSet.of(CLOSED, DELIVERED, FAILED, RETURNED)),
            Map.entry(CLOSED, EnumSet.noneOf(DeliveryStatus.class))
    );

    public static boolean canTransition(DeliveryStatus from, DeliveryStatus to) {
        if (from == null) return true;
        if (from == to) return true;
        Set<DeliveryStatus> allowed = ALLOWED.get(from);
        return allowed != null && allowed.contains(to);
    }
}
