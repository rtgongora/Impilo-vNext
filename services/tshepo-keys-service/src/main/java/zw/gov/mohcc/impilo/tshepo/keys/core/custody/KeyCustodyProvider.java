package zw.gov.mohcc.impilo.tshepo.keys.core.custody;

/**
 * Custody seam for signing-key private material (Identity Journey Program X4).
 *
 * <p>The estate's key hierarchy must be able to move from software custody
 * (private keys AES-256-GCM-encrypted in Postgres) to KMS/HSM custody without
 * touching the signing orchestration. Everything that generates key material or
 * uses a private key goes through this interface; {@code custodyBlob} is
 * whatever the provider needs to re-reach the private key — the encrypted key
 * itself for software custody, an opaque key handle for a KMS/HSM.</p>
 *
 * <p>A vendor KMS/HSM implementation registers as {@code @Primary} (or via
 * {@code impilo.keys.custody-provider}) and MUST throw
 * {@link UnsupportedOperationException} from {@link #exportPrivate(byte[])} —
 * hardware-held keys never leave the module. Until every signing path uses
 * {@link #sign(byte[], byte[])}, callers that assemble JWS objects locally
 * (Nimbus) still call {@code exportPrivate}; that is acceptable for software
 * custody only and is the residual migration surface for a real HSM.</p>
 */
public interface KeyCustodyProvider {

    /** Freshly generated Ed25519 material: raw public key + custody blob. */
    record GeneratedKeyMaterial(byte[] publicKey, byte[] custodyBlob) { }

    /** Generate a new Ed25519 key pair under this provider's custody. */
    GeneratedKeyMaterial generate();

    /** Raw Ed25519 signature over the payload using the custody blob's key. */
    byte[] sign(byte[] custodyBlob, byte[] payload);

    /**
     * Export the raw private key (software custody only — KMS/HSM providers
     * throw {@link UnsupportedOperationException}).
     */
    byte[] exportPrivate(byte[] custodyBlob);

    /**
     * Whether {@link #exportPrivate(byte[])} is permitted for this provider.
     *
     * <p>{@code true} for software custody (the raw key can be handed to a local
     * Nimbus signer); {@code false} for HSM/KMS custody, where the key never
     * leaves the module. JWS assembly consults this to choose between the
     * export-based path and a custody-{@link #sign(byte[], byte[])}-based path.</p>
     */
    default boolean supportsExport() {
        return true;
    }

    /** Custody class identifier: {@code SOFTWARE}, {@code KMS}, {@code HSM}. */
    String custodyType();
}
