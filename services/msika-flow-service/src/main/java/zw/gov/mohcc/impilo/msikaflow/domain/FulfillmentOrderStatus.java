package zw.gov.mohcc.impilo.msikaflow.domain;

public enum FulfillmentOrderStatus {
    PENDING,
    PICKING,
    PACKING,
    STAGED,
    HANDED_TO_LOGISTICS,
    IN_TRANSIT,
    READY_FOR_HANDOFF,
    DELIVERED,
    PICKED_UP,
    FAILED,
    RETURNED,
    COMPLETED
}

