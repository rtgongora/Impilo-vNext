package zw.gov.mohcc.impilo.notification.provider;

/**
 * Thrown when a notification provider fails to deliver a message.
 * Contains the channel and recipient for error tracking, but never
 * includes the message body to prevent PII leakage in logs.
 */
public class NotificationDeliveryException extends RuntimeException {

    private final String channel;
    private final String recipient;

    public NotificationDeliveryException(String channel, String recipient, Throwable cause) {
        super("Failed to deliver " + channel + " notification to " + recipient, cause);
        this.channel = channel;
        this.recipient = recipient;
    }

    public String getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }
}
