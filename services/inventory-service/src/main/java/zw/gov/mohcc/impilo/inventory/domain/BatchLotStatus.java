package zw.gov.mohcc.impilo.inventory.domain;

/**
 * Governance status of a batch/lot ({@code inv_batch_lots.status}).
 */
public enum BatchLotStatus {

    /** Usable stock. */
    ACTIVE,

    /** Held pending inspection (e.g. cold-chain excursion, damage). Blocked from use. */
    QUARANTINED,

    /** Subject to a recall. Blocked from use. */
    RECALLED,

    /** Past expiry. Blocked from use. */
    EXPIRED,

    /** Fully consumed / no remaining stock. */
    DEPLETED
}
