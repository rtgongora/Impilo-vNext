package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The content hash is what makes "the same version always says the same thing" checkable rather
 * than merely intended, so what it treats as a change — and what it does not — is load-bearing.
 */
class ConfigContentHasherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConfigContentHasher hasher = new ConfigContentHasher(objectMapper);

    @Test
    void reorderingObjectKeysIsNotAContentChange() throws Exception {
        String a = "{\"currency\":\"USD\",\"amount\":null,\"code\":\"STUDENT_INDEX\"}";
        String b = "{\"code\":\"STUDENT_INDEX\",\"currency\":\"USD\",\"amount\":null}";

        assertThat(hasher.hash(objectMapper.readTree(a)))
                .isEqualTo(hasher.hash(objectMapper.readTree(b)));
    }

    @Test
    void nestedKeyOrderIsAlsoCanonicalised() throws Exception {
        String a = "{\"fee\":{\"currency\":\"USD\",\"amount\":10},\"code\":\"X\"}";
        String b = "{\"code\":\"X\",\"fee\":{\"amount\":10,\"currency\":\"USD\"}}";

        assertThat(hasher.hash(objectMapper.readTree(a)))
                .isEqualTo(hasher.hash(objectMapper.readTree(b)));
    }

    @Test
    void reorderingAnArrayIsAContentChange() throws Exception {
        // Workflow stages in a different order are a different workflow, not the same one tidied.
        String a = "{\"stages\":[\"SUBMITTED\",\"REVIEW\",\"DECIDED\"]}";
        String b = "{\"stages\":[\"REVIEW\",\"SUBMITTED\",\"DECIDED\"]}";

        assertThat(hasher.hash(objectMapper.readTree(a)))
                .isNotEqualTo(hasher.hash(objectMapper.readTree(b)));
    }

    @Test
    void changingAValueChangesTheHash() throws Exception {
        assertThat(hasher.hash(objectMapper.readTree("{\"amount\":10}")))
                .isNotEqualTo(hasher.hash(objectMapper.readTree("{\"amount\":11}")));
    }

    @Test
    void anUnsetValueIsDistinctFromAZeroValue() throws Exception {
        // A fee the Council has not set is not a fee of zero, and the hash must not conflate them.
        assertThat(hasher.hash(objectMapper.readTree("{\"amount\":null}")))
                .isNotEqualTo(hasher.hash(objectMapper.readTree("{\"amount\":0}")));
    }

    @Test
    void theHashMatchesTheColumnConstraint() throws Exception {
        // The migration CHECKs '^[0-9a-f]{64}$'; a hash that fails it would be rejected at insert.
        assertThat(hasher.hash(objectMapper.readTree("{\"a\":1}"))).matches("^[0-9a-f]{64}$");
    }

    @Test
    void aVersionMustCarryAPayload() {
        assertThatThrownBy(() -> hasher.hash(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }
}
