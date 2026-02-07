package zw.gov.mohcc.impilo.inventory.domain;

/**
 * Operating mode for inventory management at a tenant/facility ({@code inv_capabilities.capability_mode}).
 *
 * <p>Determines whether inventory is managed entirely within the Impilo
 * platform, delegated to an external system via an adapter, or operated
 * in a hybrid dual-write mode with reconciliation.</p>
 */
public enum CapabilityMode {

    /** Inventory is tracked entirely within the Impilo platform. */
    INTERNAL,

    /** Inventory is managed by an external system (e.g. eLMIS) and synchronised via the inventory-elmis-adapter. */
    ADAPTER,

    /** Dual-write to both internal and external systems with automatic reconciliation support. */
    HYBRID
}
