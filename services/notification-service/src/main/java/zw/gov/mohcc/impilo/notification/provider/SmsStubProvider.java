package zw.gov.mohcc.impilo.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsStubProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmsStubProvider.class);

    @Override
    public String name() {
        return "sms_stub";
    }

    @Override
    public void send(String channel, String to, String subject, String body) {
        log.info("[SMS_STUB] Simulating SMS to={} body_length={}", to,
                body != null ? body.length() : 0);
    }
}
