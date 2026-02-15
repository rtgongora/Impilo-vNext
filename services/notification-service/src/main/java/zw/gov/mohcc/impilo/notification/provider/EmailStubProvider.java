package zw.gov.mohcc.impilo.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailStubProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailStubProvider.class);

    @Override
    public String name() {
        return "email_stub";
    }

    @Override
    public void send(String channel, String to, String subject, String body) {
        log.info("[EMAIL_STUB] Simulating EMAIL to={} subject={} body_length={}", to, subject,
                body != null ? body.length() : 0);
    }
}
