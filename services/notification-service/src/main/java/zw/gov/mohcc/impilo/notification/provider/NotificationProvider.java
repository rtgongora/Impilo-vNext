package zw.gov.mohcc.impilo.notification.provider;

public interface NotificationProvider {

    String name();

    void send(String channel, String to, String subject, String body);
}
