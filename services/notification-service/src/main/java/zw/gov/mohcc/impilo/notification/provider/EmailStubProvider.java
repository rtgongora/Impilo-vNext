package zw.gov.mohcc.impilo.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local-development email provider that logs messages instead of sending them.
 * <p>
 * Activated by default when {@code notification.email.provider} is not set to "smtp".
 * In production, use {@link SmtpEmailProvider} with proper SMTP configuration.
 */
@Component
@ConditionalOnProperty(name = "notification.email.provider", havingValue = "log", matchIfMissing = true)
public class EmailStubProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailStubProvider.class);

    @Override
    public String name() {
        return "email_log";
    }

    @Override
    public void send(String channel, String to, String subject, String body) {
        // Loud, not silent: the message is NOT delivered. Configure notification.email.provider=smtp.
        log.warn("[EMAIL NOT DELIVERED — stub provider] to={} subject={} body_length={}; "
                        + "set notification.email.provider=smtp for real delivery",
                to, subject, body != null ? body.length() : 0);
    }
}
