package zw.gov.mohcc.impilo.inventory.domain;

/** Lifecycle status of a customer order placed with a supplier. */
public enum SupplierOrderStatus {
    PLACED,
    CONFIRMED,
    PACKED,
    DISPATCHED,
    DELIVERED,
    CANCELLED,
    REJECTED
}
