package zw.gov.mohcc.impilo.oros.domain;

/**
 * Operating mode for order fulfillment at a facility.
 *
 * <ul>
 *   <li>{@code INTERNAL} — orders are tracked and fulfilled entirely within
 *       the Impilo platform using manual workstep progression</li>
 *   <li>{@code ADAPTER} — orders are forwarded to an external system (LIMS,
 *       PACS, pharmacy) via an adapter; results flow back automatically</li>
 *   <li>{@code HYBRID} — orders are sent to an external system but results
 *       require manual reconciliation via the reconcile queue</li>
 * </ul>
 */
public enum AdapterMode {
    INTERNAL,
    ADAPTER,
    HYBRID
}
