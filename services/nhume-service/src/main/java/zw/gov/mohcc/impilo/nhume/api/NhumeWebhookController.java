package zw.gov.mohcc.impilo.nhume.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryIntegrationProviderEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryWebhookEventEntity;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryIntegrationProviderRepository;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryWebhookEventRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives partner-courier and autonomous-provider webhooks at
 * {@code /api/v1/nhume/webhooks/{providerCode}}.
 *
 * <p>Validates a shared HMAC-SHA256 signature when the provider has a secret
 * configured, persists the raw payload for audit, and acknowledges. Real
 * processing of provider events is done downstream from the persisted event.
 */
@RestController
public class NhumeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(NhumeWebhookController.class);
    private static final String SIGNATURE_HEADER = "X-Nhume-Signature";

    private final DeliveryWebhookEventRepository webhookRepo;
    private final DeliveryIntegrationProviderRepository providerRepo;
    private final ObjectMapper objectMapper;

    @Value("${impilo.nhume.webhooks.shared-secret:}")
    private String sharedSecret;

    public NhumeWebhookController(DeliveryWebhookEventRepository webhookRepo,
                                   DeliveryIntegrationProviderRepository providerRepo,
                                   ObjectMapper objectMapper) {
        this.webhookRepo = webhookRepo;
        this.providerRepo = providerRepo;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/nhume/webhooks/{providerCode}")
    @Transactional
    public ResponseEntity<?> webhook(@PathVariable String providerCode,
                                      @RequestBody Map<String, Object> payload,
                                      HttpServletRequest http) {
        DeliveryIntegrationProviderEntity provider = providerRepo.findById(providerCode).orElse(null);
        String secret = provider != null && provider.getWebhookSecret() != null
                ? provider.getWebhookSecret() : sharedSecret;
        String signatureHeader = http.getHeader(SIGNATURE_HEADER);
        String rawJson = toJson(payload);
        boolean valid = verify(secret, signatureHeader, rawJson);

        DeliveryWebhookEventEntity e = new DeliveryWebhookEventEntity();
        e.setProviderCode(providerCode);
        e.setEventKind(asString(payload.get("event_kind")));
        if (payload.get("delivery_id") instanceof String s) {
            try { e.setDeliveryId(java.util.UUID.fromString(s)); } catch (Exception ignored) {}
        }
        if (payload.get("mission_id") instanceof String s) {
            try { e.setMissionId(java.util.UUID.fromString(s)); } catch (Exception ignored) {}
        }
        e.setSignatureValid(valid);
        e.setPayloadJson(rawJson);
        webhookRepo.save(e);

        if (!valid) {
            log.warn("[nhume-webhook] invalid signature provider={} event={}", providerCode,
                    e.getEventKind());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "WEBHOOK_SIGNATURE_INVALID");
            body.put("message", "Webhook signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        log.info("[nhume-webhook] accepted provider={} event={}", providerCode, e.getEventKind());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "accepted");
        body.put("event_id", e.getId());
        return ResponseEntity.ok(body);
    }

    private boolean verify(String secret, String header, String body) {
        if (secret == null || secret.isBlank()) return true;
        if (header == null || header.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(computed);
            String supplied = header.startsWith("sha256=") ? header.substring(7) : header;
            return constantTimeEquals(expected, supplied);
        } catch (Exception e) {
            log.warn("[nhume-webhook] signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private String asString(Object o) { return o == null ? null : o.toString(); }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }
}
