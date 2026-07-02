package zw.gov.mohcc.impilo.notification.domain;

public enum NotificationStatus {
    PENDING,
    SENT,
    /** Confirmed received on the recipient's channel (via provider delivery receipt). */
    DELIVERED,
    FAILED,
    /** Not sent — blocked by policy or superseded by communication preference change. */
    CANCELLED
}
