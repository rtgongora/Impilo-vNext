package zw.gov.mohcc.impilo.pharmacy.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupMethod;
import zw.gov.mohcc.impilo.pharmacy.domain.PickupStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PickupProofEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3 BUG-5 + BUG-9 contracts.
 *
 * <p>BUG-5 — the one-time credential is returned EXACTLY ONCE, via the
 * creation mapping; the read mapping never carries it, and the plaintext is
 * never persisted (transient field, hash-only storage).</p>
 *
 * <p>BUG-9 — every {@link PickupMethod} enum name must fit the widened
 * {@code rx_pickup_proofs.method} column (V008, VARCHAR(32)); SMS_REFERENCE
 * (13 chars) was schema-dead under the original VARCHAR(10).</p>
 */
class PickupProofDtoCredentialContractTest {

    private PickupProofEntity entity() {
        PickupProofEntity e = new PickupProofEntity();
        e.setProofId(UUID.randomUUID());
        e.setDispenseOrderId(UUID.randomUUID());
        e.setMethod(PickupMethod.SMS_REFERENCE);
        e.setStatus(PickupStatus.PENDING);
        e.setNamedRecipient("Thandiwe Moyo");
        e.setTokenHash("deadbeef");
        return e;
    }

    @Test
    @DisplayName("BUG-5: creation mapping carries the one-time credential; read mapping never does")
    void credentialOnlyOnCreationMapping() {
        PickupProofEntity e = entity();
        e.setOneTimeCredential("KMNPQR23");

        assertThat(PickupProofDto.created(e, e.getOneTimeCredential()).oneTimeCredential())
                .isEqualTo("KMNPQR23");
        assertThat(PickupProofDto.from(e).oneTimeCredential()).isNull();
    }

    @Test
    @DisplayName("BUG-5: the transient credential never rides the persisted deviceFingerprint column")
    void credentialNeverOnPersistedColumn() {
        PickupProofEntity e = entity();
        e.setOneTimeCredential("734291");
        assertThat(e.getDeviceFingerprint()).isNull();
    }

    @Test
    @DisplayName("BUG-9: every PickupMethod name fits the widened VARCHAR(32) method column")
    void everyPickupMethodFitsWidenedColumn() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V008__widen_pickup_proof_method.sql");
        String sql = Files.readString(migration);
        assertThat(sql).contains("ALTER TABLE rx_pickup_proofs ALTER COLUMN method TYPE VARCHAR(32)");
        for (PickupMethod method : PickupMethod.values()) {
            assertThat(method.name().length())
                    .as("PickupMethod.%s must fit VARCHAR(32)", method)
                    .isLessThanOrEqualTo(32);
        }
    }
}
