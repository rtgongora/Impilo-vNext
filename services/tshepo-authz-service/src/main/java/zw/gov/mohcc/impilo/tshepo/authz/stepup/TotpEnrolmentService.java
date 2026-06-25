package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TotpEnrolmentEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.TotpEnrolmentRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Authenticator-app TOTP enrolment (RFC 6238): generates a per-actor shared secret, stores it
 * AES-256-GCM encrypted at rest, and returns the {@code otpauth://} provisioning URI for the
 * authenticator app. A code must be confirmed before the enrolment becomes ACTIVE.
 *
 * <p>Gated on {@code tshepo.authz.totp.enrolment-enabled=true}; when disabled the fail-closed
 * default {@code TotpSecretProvider} stays and TOTP step-up cannot complete.</p>
 */
@Service
@ConditionalOnProperty(name = "tshepo.authz.totp.enrolment-enabled", havingValue = "true")
public class TotpEnrolmentService {

    private static final Logger log = LoggerFactory.getLogger(TotpEnrolmentService.class);
    private static final int SECRET_BYTES = 20; // 160-bit, RFC 4226/6238 recommended for SHA-1

    private final TotpEnrolmentRepository repository;
    private final TotpSecretCipher cipher;
    private final AuthzProperties properties;

    public TotpEnrolmentService(TotpEnrolmentRepository repository,
                                TotpSecretCipher cipher,
                                AuthzProperties properties) {
        this.repository = repository;
        this.cipher = cipher;
        this.properties = properties;
    }

    /** Provisioning result handed to the client to seed an authenticator app. */
    public record EnrolmentResult(String otpauthUri, String base32Secret, String issuer) {}

    @Transactional
    public EnrolmentResult enrol(UUID tenantId, String actorId) {
        AuthzProperties.Totp cfg = properties.getTotp();
        byte[] secret = TotpCodes.randomSecret(SECRET_BYTES);
        TotpSecretCipher.Encrypted enc = cipher.encrypt(secret);

        TotpEnrolmentEntity entity = repository.findByTenantIdAndActorId(tenantId, actorId)
                .orElseGet(TotpEnrolmentEntity::new);
        entity.setTenantId(tenantId);
        entity.setActorId(actorId);
        entity.setSecretCipher(enc.cipherBase64());
        entity.setSecretIv(enc.ivBase64());
        entity.setAlgorithm("SHA1");
        entity.setDigits(cfg.getDigits());
        entity.setPeriod(cfg.getPeriod());
        entity.setStatus("PENDING_CONFIRM");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setConfirmedAt(null);
        entity.setRevokedAt(null);
        repository.save(entity);

        String base32 = TotpCodes.base32Encode(secret);
        String otpauth = buildOtpauthUri(cfg, actorId, base32);
        log.info("TOTP enrolment issued (pending confirm): tenant={} actor={}", tenantId, actorId);
        return new EnrolmentResult(otpauth, base32, cfg.getIssuer());
    }

    /** Confirm a code to activate a pending enrolment. Returns false if the code is wrong. */
    @Transactional
    public boolean confirm(UUID tenantId, String actorId, String code) {
        TotpEnrolmentEntity entity = repository.findByTenantIdAndActorId(tenantId, actorId)
                .orElseThrow(() -> new IllegalArgumentException("No TOTP enrolment for actor " + actorId));
        byte[] secret = cipher.decrypt(entity.getSecretCipher(), entity.getSecretIv());
        if (!TotpCodes.verify(secret, code, entity.getDigits(), entity.getPeriod())) {
            return false;
        }
        entity.setStatus("ACTIVE");
        entity.setConfirmedAt(OffsetDateTime.now());
        entity.setRevokedAt(null);
        repository.save(entity);
        log.info("TOTP enrolment activated: tenant={} actor={}", tenantId, actorId);
        return true;
    }

    @Transactional
    public void revoke(UUID tenantId, String actorId) {
        repository.findByTenantIdAndActorId(tenantId, actorId).ifPresent(entity -> {
            entity.setStatus("REVOKED");
            entity.setRevokedAt(OffsetDateTime.now());
            repository.save(entity);
            log.info("TOTP enrolment revoked: tenant={} actor={}", tenantId, actorId);
        });
    }

    private static String buildOtpauthUri(AuthzProperties.Totp cfg, String actorId, String base32) {
        String issuer = enc(cfg.getIssuer());
        String label = issuer + ":" + enc(actorId);
        return "otpauth://totp/" + label
                + "?secret=" + base32
                + "&issuer=" + issuer
                + "&algorithm=SHA1"
                + "&digits=" + cfg.getDigits()
                + "&period=" + cfg.getPeriod();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
