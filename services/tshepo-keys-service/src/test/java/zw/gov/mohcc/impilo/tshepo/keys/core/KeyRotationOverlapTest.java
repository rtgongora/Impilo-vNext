package zw.gov.mohcc.impilo.tshepo.keys.core;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;
import zw.gov.mohcc.impilo.tshepo.keys.core.custody.ConfigKekProvider;
import zw.gov.mohcc.impilo.tshepo.keys.core.custody.SoftwareKeyCustodyProvider;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.KeyRotationLogRepository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.SigningKeyRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W4d: K1&rarr;K2 rotation-overlap proof.
 *
 * <p>A credential signed under the previous kid (K1) must still verify after the tenant rotates
 * to a new key (K2), so issued cards/QRs survive to their expiry. Uses the real
 * {@link KeyRotationService}, {@link Ed25519SigningService} and {@link JwksService} over an
 * in-memory-backed repository.</p>
 */
class KeyRotationOverlapTest {

    private static final String KEK_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final UUID TENANT = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    @SuppressWarnings("unchecked")
    void credentialSignedUnderK1_stillVerifiesAfterRotationToK2_andJwksPublishesBoth() {
        KeysProperties props = new KeysProperties();
        props.setKek(KEK_HEX);
        props.setRotationIntervalDays(90);

        // In-memory-backed SigningKeyRepository
        Map<String, SigningKeyEntity> store = new ConcurrentHashMap<>();
        AtomicLong seq = new AtomicLong(1);
        SigningKeyRepository repo = mock(SigningKeyRepository.class);
        when(repo.save(any(SigningKeyEntity.class))).thenAnswer(inv -> {
            SigningKeyEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(seq.getAndIncrement());
            }
            if (e.getCreatedAt() == null) {
                e.setCreatedAt(Instant.now().plusNanos(seq.get()));
            }
            store.put(e.getKeyId(), e);
            return e;
        });
        when(repo.findByKeyId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get((String) inv.getArgument(0))));
        when(repo.findActiveKeysByTenant(any(UUID.class))).thenAnswer(inv -> {
            UUID t = inv.getArgument(0);
            List<SigningKeyEntity> out = new ArrayList<>(store.values().stream()
                    .filter(k -> t.equals(k.getTenantId()) && "ACTIVE".equals(k.getStatus()))
                    .toList());
            return out;
        });
        when(repo.findAllVerificationKeys(any(Instant.class))).thenAnswer(inv -> {
            Instant now = inv.getArgument(0);
            return store.values().stream()
                    .filter(k -> ("ACTIVE".equals(k.getStatus()) || "ROTATED".equals(k.getStatus()))
                            && (k.getExpiresAt() == null || k.getExpiresAt().isAfter(now)))
                    .toList();
        });

        EventOutboxRepository outbox = mock(EventOutboxRepository.class);
        when(outbox.save(any())).thenAnswer(inv -> inv.getArgument(0));
        KeyRotationLogRepository rotationLog = mock(KeyRotationLogRepository.class);
        when(rotationLog.save(any())).thenAnswer(inv -> inv.getArgument(0));
        StringRedisTemplate redis = mock(StringRedisTemplate.class); // getJwksMap does not touch redis

        SoftwareKeyCustodyProvider custody = new SoftwareKeyCustodyProvider(new ConfigKekProvider(props));
        Ed25519SigningService signing = new Ed25519SigningService(repo, outbox, props, custody);
        KeyRotationService rotation = new KeyRotationService(repo, rotationLog, outbox, signing, props);
        JwksService jwks = new JwksService(repo, redis, props);

        // K1: mint + sign a credential under it
        SigningKeyEntity k1 = signing.generateKeyPair(TENANT);
        String k1Kid = k1.getKeyId();
        String credential = signing.signJwsWithKey(k1, "{\"card\":\"card-001\",\"holder\":\"citizen-1\"}");
        assertThat(signing.verifyJws(credential)).isTrue();

        // Rotate to K2
        SigningKeyEntity k2 = rotation.rotateKey(TENANT, "admin", "quarterly");
        String k2Kid = k2.getKeyId();
        assertThat(k2Kid).isNotEqualTo(k1Kid);
        assertThat(store.get(k1Kid).getStatus()).isEqualTo("ROTATED");
        assertThat(store.get(k2Kid).getStatus()).isEqualTo("ACTIVE");

        // OVERLAP: the K1-signed credential still verifies after rotation
        assertThat(signing.verifyJws(credential))
                .as("pre-rotation credential must still verify during overlap")
                .isTrue();

        // A fresh credential under the new active key also verifies
        String newCred = signing.signJwsWithKey(
                signing.getCurrentActiveKey(TENANT), "{\"card\":\"card-002\"}");
        assertThat(signing.verifyJws(newCred)).isTrue();

        // JWKS publishes BOTH kids during overlap (external verifiers can resolve K1 and K2)
        Map<String, Object> jwksMap = jwks.getJwksMap();
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwksMap.get("keys");
        List<String> kids = keys.stream().map(k -> (String) k.get("kid")).toList();
        assertThat(kids).contains(k1Kid, k2Kid);

        // Revocation removes a key from verification immediately (compromise response)
        rotation.revokeKey(k1Kid, "security");
        assertThat(store.get(k1Kid).getStatus()).isEqualTo("REVOKED");
        List<Map<String, Object>> afterRevoke =
                (List<Map<String, Object>>) jwks.getJwksMap().get("keys");
        assertThat(afterRevoke.stream().map(k -> (String) k.get("kid")).toList())
                .doesNotContain(k1Kid);
    }
}
