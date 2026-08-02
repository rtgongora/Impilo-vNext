package zw.gov.mohcc.impilo.tshepo.contracts.v1;

/**
 * Version marker for canonical Tshepo trust-plane contracts (Checkpoint 2).
 *
 * <p>Legacy DTOs under {@code zw.gov.mohcc.impilo.tshepo.contracts.dto} remain on the
 * wire for proven callers; adapters convert without broadening authority.</p>
 */
public final class TrustContractVersion {

    /** Canonical contract family for Checkpoint 2 types. */
    public static final String V1 = "tshepo.trust.v1";

    private TrustContractVersion() {}

    public static void requireV1(String version) {
        if (!V1.equals(version)) {
            throw new IllegalArgumentException(
                    "Unsupported trust contract version: " + version + " (required " + V1 + ")");
        }
    }
}
