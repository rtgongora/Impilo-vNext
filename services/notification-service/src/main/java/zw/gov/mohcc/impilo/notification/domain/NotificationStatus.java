package zw.gov.mohcc.impilo.notification.domain;

public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    /** Not sent — blocked by policy or superseded by communication preference change. */
    CANCELLED
}
