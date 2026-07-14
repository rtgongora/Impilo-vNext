package zw.gov.mohcc.impilo.wallet.card;

/**
 * Supplies the AES-256 data-encryption key used to protect card health data at rest.
 *
 * <p>This is the single seam between card PHR encryption and key custody. The default
 * implementation ({@link MasterKeyCardDataKeyProvider}) derives the key from a fail-closed
 * deployment master secret. When the platform gains a managed data-key/KMS service, add a
 * {@code CardDataKeyProvider} bean that fetches/unwraps the key from the KMS — it will take
 * precedence over the master-key default automatically, and no other code changes.</p>
 */
public interface CardDataKeyProvider {

    /**
     * Return the 32-byte AES-256 key for the given per-record key reference. The same {@code keyRef}
     * must always yield the same key (the reference is stored with each record so the data can be
     * decrypted later); different references must yield different keys.
     *
     * @param keyRef the per-record key reference (e.g. {@code tshepo-keys:<tenant>:patient:<cpid>})
     * @return a 32-byte AES-256 key
     */
    byte[] dataKey(String keyRef);
}
