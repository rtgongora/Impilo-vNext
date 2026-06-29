package zw.gov.mohcc.impilo.inventory.domain;

/** The kind of record being synced to an external system. */
public enum SyncEntityType {
    STOCK_ON_HAND,
    CONSUMPTION,
    ORDER,
    RECEIPT,
    STOCK_STATUS,
    REQUISITION
}
