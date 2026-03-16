package zw.gov.mohcc.impilo.notification.provider;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * SMTP-based email notification provider.
 * <p>
 * Activated when {@code notification.email.provider=smtp} is set.
 * Connects to a configured SMTP server to send real email notifications.
 * Falls back gracefully with clear error logging when SMTP is unreachable.
 */
@Component
@ConditionalOnProperty(name = "notification.email.provider", havingValue = "smtp", matchIfMissing = false)
public class SmtpEmailProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);

    private final Session mailSession;
    private final String fromAddress;

    public SmtpEmailProvider(
            @Value("${notification.email.smtp.host:localhost}") String host,
            @Value("${notification.email.smtp.port:587}") int port,
            @Value("${notification.email.smtp.username:}") String username,
            @Value("${notification.email.smtp.password:}") String password,
            @Value("${notification.email.smtp.from:noreply@impilo.gov.zw}") String fromAddress,
            @Value("${notification.email.smtp.starttls:true}") boolean starttls,
            @Value("${notification.email.smtp.auth:true}") boolean auth) {

        this.fromAddress = fromAddress;

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        if (auth && username != null && !username.isBlank()) {
            this.mailSession = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        } else {
            this.mailSession = Session.getInstance(props);
        }

        log.info("SMTP email provider initialised: host={}, port={}, from={}", host, port, fromAddress);
    }

    @Override
    public String name() {
        return "smtp_email";
    }

    @Override
    public void send(String channel, String to, String subject, String body) {
        try {
            MimeMessage message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(fromAddress));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject != null ? subject : "Impilo Notification");

            if (body != null && body.trim().startsWith("<")) {
                message.setContent(body, "text/html; charset=utf-8");
            } else {
                message.setText(body != null ? body : "", "utf-8");
            }

            Transport.send(message);
            log.info("Email sent successfully: to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email: to={}, subject={}, error={}", to, subject, e.getMessage());
            throw new NotificationDeliveryException("EMAIL", to, e);
        }
    }
}
