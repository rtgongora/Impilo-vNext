package zw.gov.mohcc.impilo.tshepo.keys.core.custody;

/**
 * Source seam for the Key-Encryption-Key (KEK) that {@link SoftwareKeyCustodyProvider}
 * uses to AES-256-GCM-encrypt Ed25519 private keys at rest (W4c key-custody hardening).
 *
 * <p>The KEK is the root of the software-custody hierarchy: whoever holds it can decrypt
 * every stored private key. Making its <em>origin</em> swappable — config today, Vault/KMS
 * later — means the estate can move KEK custody off the config file without touching the
 * signing orchestration. A vendor implementation registers via
 * {@code impilo.keys.kek-source} and returns the 32-byte AES-256 key however it reaches it
 * (unseal from Vault, unwrap from a KMS, read from an HSM-fronted secret).</p>
 */
public interface KekProvider {

    /**
     * The raw 32-byte (256-bit) AES KEK.
     *
     * @throws IllegalStateException         if the configured source cannot yield a valid KEK
     * @throws UnsupportedOperationException if the source is declared but not yet wired
     */
    byte[] getKek();

    /** KEK source identifier: {@code CONFIG}, {@code VAULT}, {@code KMS}. */
    String sourceType();
}
