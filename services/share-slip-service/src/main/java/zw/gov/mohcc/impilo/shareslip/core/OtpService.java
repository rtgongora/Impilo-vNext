package zw.gov.mohcc.impilo.shareslip.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.shareslip.config.ShareSlipProperties;
import zw.gov.mohcc.impilo.shareslip.dto.OtpDeliveryResponse;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

/**
 * OTP generation, hashing, and verification service.
 *
 * Uses HmacService from shared-core for deterministic, non-reversible OTP hashing.
 * In development mode, OTPs are logged to console. In production, they would be
 * delivered via notification-service (SMS/EMAIL).
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final HmacService hmacService;
    private final ShareSlipProperties properties;

    public OtpService(HmacService hmacService, ShareSlipProperties properties) {
        this.hmacService = hmacService;
        this.properties = properties;
    }

    /**
     * Generate a random numeric OTP of the configured length.
     *
     * @param length the number of digits in the OTP
     * @return a numeric OTP string (zero-padded)
     */
    public String generateOtp(int length) {
        int bound = (int) Math.pow(10, length);
        int otpValue = SECURE_RANDOM.nextInt(bound);
        return String.format("%0" + length + "d", otpValue);
    }

    /**
     * Hash an OTP using HMAC-SHA256 for secure storage.
     * The hash is deterministic for the same OTP and pepper.
     *
     * @param otp the plaintext OTP
     * @return hex-encoded HMAC hash
     */
    public String hashOtp(String otp) {
        return hmacService.computeLookupHash(otp);
    }

    /**
     * Verify an OTP against a stored hash using constant-time comparison.
     *
     * @param otp        the plaintext OTP provided by the user
     * @param storedHash the stored HMAC hash
     * @return true if the OTP matches
     */
    public boolean verifyOtp(String otp, String storedHash) {
        if (otp == null || storedHash == null) {
            return false;
        }
        return hmacService.verifyLookupHash(otp, storedHash);
    }

    /**
     * Send an OTP to the specified destination.
     * In development mode, the OTP is logged. In production, this would
     * integrate with notification-service for SMS or EMAIL delivery.
     *
     * @param channel     the delivery channel (SMS or EMAIL)
     * @param destination the phone number or email address
     * @param otp         the plaintext OTP to send
     * @return delivery status
     */
    public OtpDeliveryResponse sendOtp(String channel, String destination, String otp) {
        // In development: log the OTP for testing purposes
        log.info("[DEV] OTP for {} via {}: {} (would be sent via notification-service in production)",
                destination, channel, otp);

        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plusMinutes(properties.getOtp().getExpiryMinutes());

        return new OtpDeliveryResponse(true, expiresAt, channel);
    }
}
