package zw.gov.mohcc.impilo.mushex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Webhook signature verification service.
 *
 * Validates HMAC-SHA256 signatures on inbound payment adapter webhooks
 * to ensure payloads have not been tampered with in transit.
 */
@Service
public class WebhookSecurityService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecurityService.class);

    private final HmacService hmacService;

    public WebhookSecurityService(HmacService hmacService) {
        this.hmacService = hmacService;
    }

    /**
     * Verify that a webhook payload matches the provided HMAC signature.
     *
     * @param payload   the raw webhook body
     * @param signature the HMAC-SHA256 hex signature provided by the caller
     * @return true if the signature is valid and matches the payload
     */
    public boolean verifyWebhook(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            log.warn("Webhook verification failed: payload or signature is null/blank");
            return false;
        }

        try {
            return hmacService.verify(payload, signature);
        } catch (Exception e) {
            log.error("Webhook signature verification error", e);
            return false;
        }
    }
}
