package zw.gov.mohcc.impilo.experience.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * Per-account / per-IP fixed-window throttle for record-claim starts (Identity Journey
 * Doctrine §2 anti-abuse). The claim second factor already locks a single challenge after
 * a few wrong OTPs, but that does not stop one account from <b>starting</b> claims against
 * many different records (a claim-driven enumeration probe). This caps how many claim starts
 * an account (and a source IP) may make in a window, on top of the private matching + uniform
 * responses + owner-alert already in place.
 *
 * <p>Counting lives in Redis (the BFF is stateless), mirroring {@code ContactOtpService}'s
 * fixed-window pattern. It fails <b>open</b> if Redis is unavailable: the downstream claim
 * still requires possession of the OTP delivered to the record's own recorded contact, so a
 * throttle-count outage cannot by itself complete a takeover — availability is preserved for
 * legitimate citizens during an infra blip.</p>
 */
@Service
public class ClaimThrottleService {

    private static final Logger log = LoggerFactory.getLogger(ClaimThrottleService.class);

    private static final String ACCOUNT_PREFIX = "claim:start:acct:";
    private static final String IP_PREFIX = "claim:start:ip:";
    private static final int MAX_STARTS_PER_ACCOUNT = 5;
    private static final int MAX_STARTS_PER_IP = 20;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public ClaimThrottleService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @return true if this claim start is within limits and may proceed; false if the account
     *         or IP has exceeded its window (the caller should return the uniform generic
     *         response without resolving or dispatching an OTP).
     */
    public boolean allowStart(String tenantId, String accountRef, String clientIp) {
        try {
            boolean accountOk = accountRef == null || accountRef.isBlank()
                    || within(ACCOUNT_PREFIX + tenantId + ":" + hash(accountRef), MAX_STARTS_PER_ACCOUNT);
            boolean ipOk = clientIp == null || clientIp.isBlank()
                    || within(IP_PREFIX + tenantId + ":" + hash(clientIp), MAX_STARTS_PER_IP);
            if (!accountOk || !ipOk) {
                log.warn("record-claim start throttled (accountOk={}, ipOk={})", accountOk, ipOk);
                return false;
            }
            return true;
        } catch (Exception e) {
            // Fail open — the OTP-to-recorded-contact second factor remains the hard gate.
            log.warn("claim throttle unavailable, allowing (fail-open): {}", e.getMessage());
            return true;
        }
    }

    private boolean within(String key, int max) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
        return count == null || count <= max;
    }

    private static String hash(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
