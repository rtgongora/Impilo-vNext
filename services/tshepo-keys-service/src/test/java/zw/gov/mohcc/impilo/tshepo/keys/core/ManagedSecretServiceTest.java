package zw.gov.mohcc.impilo.tshepo.keys.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.ManagedSecretEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.ManagedSecretRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Managed-secret custody round-trips through AES-256-GCM (value never stored in the
 * clear), rotates in place, and resolves only ACTIVE secrets.
 */
class ManagedSecretServiceTest {

    private static final String KEK = "0".repeat(64); // 32-byte AES-256 KEK
    private final UUID tenant = UUID.randomUUID();
    private ManagedSecretRepository repo;
    private ManagedSecretService service;
    private final Map<String, ManagedSecretEntity> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        KeysProperties props = new KeysProperties();
        props.setKek(KEK);
        repo = mock(ManagedSecretRepository.class);
        store.clear();
        when(repo.save(any(ManagedSecretEntity.class))).thenAnswer(inv -> {
            ManagedSecretEntity e = inv.getArgument(0);
            store.put(e.getTenantId() + "|" + e.getSecretRef(), e);
            return e;
        });
        when(repo.findByTenantIdAndSecretRefAndStatus(any(), any(), eq("ACTIVE")))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0) + "|" + inv.getArgument(1))));
        service = new ManagedSecretService(repo, props);
    }

    @Test
    @DisplayName("register → encrypted at rest (not plaintext) → resolves back")
    void roundTrip() {
        service.registerOrRotate(tenant, "deid:dataset-1", "DEID_TOKENISER", "super-secret-value");
        ManagedSecretEntity stored = store.values().iterator().next();
        assertThat(new String(stored.getValueEncrypted())).doesNotContain("super-secret-value");
        assertThat(service.resolve(tenant, "deid:dataset-1")).contains("super-secret-value");
    }

    @Test
    @DisplayName("rotate replaces the value in place")
    void rotate() {
        service.registerOrRotate(tenant, "ref", "P", "v1");
        service.registerOrRotate(tenant, "ref", "P", "v2");
        assertThat(service.resolve(tenant, "ref")).contains("v2");
        assertThat(store).hasSize(1);
    }

    @Test
    @DisplayName("unknown ref → empty; blank value → rejected; bad KEK → fail-fast")
    void guards() {
        assertThat(service.resolve(tenant, "nope")).isEmpty();
        assertThrows(IllegalArgumentException.class,
                () -> service.registerOrRotate(tenant, "r", "P", "  "));
        KeysProperties bad = new KeysProperties();
        bad.setKek("abcd");
        assertThrows(IllegalStateException.class, () -> new ManagedSecretService(mock(ManagedSecretRepository.class), bad));
    }
}
