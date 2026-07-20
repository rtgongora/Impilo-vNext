package zw.gov.mohcc.impilo.tshepo.keys.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;
import zw.gov.mohcc.impilo.tshepo.keys.core.custody.KeyCustodyProvider;
import zw.gov.mohcc.impilo.tshepo.keys.core.custody.SoftwareKeyCustodyProvider;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.SigningKeyRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W4c: JWS-on-HSM. Proves that {@link Ed25519SigningService#signJwsWithKey} produces a
 * standard, verifiable EdDSA JWS <b>without ever exporting the private key</b> when the
 * custody provider forbids export (as a real HSM does) — the compact JWS is assembled from
 * a custody {@code sign()} over the JWS signing input.
 */
class HsmJwsAssemblyTest {

    private static final String KEK_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final UUID TENANT = UUID.fromString("44444444-4444-4444-4444-444444444444");

    /** Custody provider that signs like software but refuses export — mimics an HSM. */
    static final class NonExportableCustody implements KeyCustodyProvider {
        private final SoftwareKeyCustodyProvider delegate;
        final AtomicBoolean exportAttempted = new AtomicBoolean(false);

        NonExportableCustody(KeysProperties props) {
            this.delegate = new SoftwareKeyCustodyProvider(props);
        }

        @Override public GeneratedKeyMaterial generate() { return delegate.generate(); }
        @Override public byte[] sign(byte[] blob, byte[] payload) { return delegate.sign(blob, payload); }
        @Override public byte[] exportPrivate(byte[] blob) {
            exportAttempted.set(true);
            throw new UnsupportedOperationException("HSM keys are non-exportable");
        }
        @Override public boolean supportsExport() { return false; }
        @Override public String custodyType() { return "HSM"; }
    }

    @Test
    void signJws_onNonExportableCustody_isVerifiableAndNeverExports() {
        KeysProperties props = new KeysProperties();
        props.setKek(KEK_HEX);

        SigningKeyRepository repo = mock(SigningKeyRepository.class);
        EventOutboxRepository outbox = mock(EventOutboxRepository.class);
        when(repo.save(any(SigningKeyEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outbox.save(any(EventOutboxEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        NonExportableCustody hsm = new NonExportableCustody(props);
        Ed25519SigningService service = new Ed25519SigningService(repo, outbox, props, hsm);

        SigningKeyEntity key = service.generateKeyPair(TENANT);
        when(repo.findByKeyId(anyString())).thenReturn(Optional.of(key));

        String jws = service.signJwsWithKey(key, "{\"card\":\"c-42\",\"iss\":\"impilo\"}");

        // Standard 3-part compact JWS
        assertThat(jws.split("\\.")).hasSize(3);
        // Verifies against the public key (no export needed to verify)
        assertThat(service.verifyJws(jws)).isTrue();
        // The private key was NEVER exported on the HSM JWS path
        assertThat(hsm.exportAttempted).isFalse();
    }
}
