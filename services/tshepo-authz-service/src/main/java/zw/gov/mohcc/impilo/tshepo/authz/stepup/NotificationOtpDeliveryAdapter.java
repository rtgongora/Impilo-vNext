package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Real SMS-OTP delivery adapter that hands the one-time passcode to the notification service
 * (the platform's communication system-of-record), replacing the fail-closed default when
 * {@code tshepo.authz.otp-delivery.notification-enabled=true}.
 *
 * <p>Ownership boundary: authz owns the OTP lifecycle (generate/hash/verify); the notification
 * service owns channel delivery, recipient contact resolution, communication-preference gating,
 * and the actual SMS gateway. This adapter only performs the authz→notification hand-off and
 * <strong>fails closed</strong> ({@link StepUpUnavailableException}) if the notification service
 * is unreachable or rejects the request, so a challenge is never issued without the OTP being
 * accepted for delivery.</p>
 *
 * <p><strong>External dependency (honest):</strong> {@code /internal/v1/notify} is an enqueue —
 * a 2xx means accepted-for-delivery, not delivered. Real SMS delivery requires the notification
 * service to have a configured SMS gateway and to resolve the recipient reference to a phone
 * number; until then it fails closed loudly at that layer (see G033). Enable this adapter only
 * where that gateway exists.</p>
 */
@Component
@ConditionalOnProperty(name = "tshepo.authz.otp-delivery.notification-enabled", havingValue = "true")
public class NotificationOtpDeliveryAdapter implements StepUpProviders.OtpDeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(NotificationOtpDeliveryAdapter.class);

    private final RestTemplate restTemplate;
    private final AuthzProperties properties;

    public NotificationOtpDeliveryAdapter(RestTemplate restTemplate, AuthzProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public void deliver(UUID tenantId, String actorId, String otp) {
        AuthzProperties.OtpDelivery cfg = properties.getOtpDelivery();
        String url = properties.getNotificationServiceUrl() + properties.getNotificationNotifyPath();
        // Recipient is referenced by actor; the notification service resolves the contact +
        // applies communication-preference gating. inboxRecipient mirrors the actor reference.
        String actorRef = "actor:" + actorId;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateKey", cfg.getTemplateKey());
        body.put("channel", cfg.getChannel());
        body.put("to", actorRef);
        body.put("inboxRecipient", actorRef);
        body.put("patientRef", actorId);
        body.put("messageKind", "SENSITIVE");
        body.put("variables", Map.of("otp", otp));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Notification service requires the companion request context.
        headers.set(CompanionHeaders.TENANT_ID, tenantId.toString());
        headers.set(CompanionHeaders.POD_ID, "tshepo-authz");
        headers.set(CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString());
        headers.set(CompanionHeaders.CORRELATION_ID, UUID.randomUUID().toString());

        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, org.springframework.http.HttpMethod.POST,
                            new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new StepUpUnavailableException(
                        "Notification service rejected OTP delivery: HTTP " + response.getStatusCode());
            }
            log.info("SMS-OTP step-up handed to notification service: tenant={} channel={} actorRef={}",
                    tenantId, cfg.getChannel(), actorRef);
        } catch (StepUpUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // Fail closed — the challenge must not be issued if delivery cannot be accepted.
            throw new StepUpUnavailableException(
                    "SMS-OTP delivery via notification service failed: " + e.getMessage());
        }
    }
}
