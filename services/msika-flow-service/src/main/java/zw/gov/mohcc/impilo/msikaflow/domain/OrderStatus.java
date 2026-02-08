package zw.gov.mohcc.impilo.msikaflow.domain;

public enum OrderStatus {
    CREATED,
    VALIDATED,
    PRICED,
    PAYMENT_PENDING,
    PAID,
    ROUTED,
    ACCEPTED,
    IN_PROGRESS,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    BOOKED,
    COLLECTED,
    DELIVERED,
    ATTENDED,
    COMPLETED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == REFUNDED || this == FAILED;
    }
}
