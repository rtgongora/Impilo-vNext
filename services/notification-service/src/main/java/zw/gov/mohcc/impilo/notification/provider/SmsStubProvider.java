package zw.gov.mohcc.impilo.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local-development SMS provider that logs messages instead of sending them.
 * <p>
 * Activated by default when {@code notification.sms.provider} is not set to "http".
 * In production, use {@link HttpSmsProvider} with a real SMS gateway.
 */
@Component
@ConditionalOnProperty(name = "notification.sms.provider", havingValue = "log", matchIfMissing = true)
public class SmsStubProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmsStubProvider.class);

    @Override
    public String name() {
        return "sms_log";
    }

    @Override
    public void send(String channel, String to, String subject, String body) {
        log.info("[SMS_DEV] to={} body_length={}", to,
                body != null ? body.length() : 0);
        if (log.isDebugEnabled() && body != null) {
            log.debug("[SMS_DEV] body: {}", body);
        }
    }
}
