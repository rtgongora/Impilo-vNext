package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DatasetTokeniser — per-dataset HMAC subject tokens")
class DatasetTokeniserTest {

    private static final Map<String, String> SECRETS = Map.of(
            "dataset-a-secret-ref", "secret-material-for-dataset-a",
            "dataset-b-secret-ref", "secret-material-for-dataset-b");

    private final SecretResolver resolver = ref -> {
        String secret = SECRETS.get(ref);
        if (secret == null) {
            throw new IllegalStateException("De-identification secret not available for reference '" + ref + "'");
        }
        return secret;
    };

    private final DatasetTokeniser tokeniser = new DatasetTokeniser(resolver);

    @Test
    @DisplayName("token is deterministic for the same dataset + cpid")
    void deterministicPerDataset() {
        String first = tokeniser.subjectToken("dataset-a-secret-ref", "CPID-001");
        String second = tokeniser.subjectToken("dataset-a-secret-ref", "CPID-001");
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("same cpid yields DIFFERENT tokens in different datasets (no cross-dataset join key)")
    void datasetScoped() {
        String tokenA = tokeniser.subjectToken("dataset-a-secret-ref", "CPID-001");
        String tokenB = tokeniser.subjectToken("dataset-b-secret-ref", "CPID-001");
        assertThat(tokenA).isNotEqualTo(tokenB);
    }

    @Test
    @DisplayName("token never equals the cpid")
    void tokenNeverEqualsCpid() {
        String cpid = "CPID-42";
        String token = tokeniser.subjectToken("dataset-a-secret-ref", cpid);
        assertThat(token).isNotEqualTo(cpid);
        assertThat(token).doesNotContain(cpid);
    }

    @Test
    @DisplayName("token is 64-char lower-case hex (HMAC-SHA256)")
    void tokenIsHmacSha256Hex() {
        String token = tokeniser.subjectToken("dataset-a-secret-ref", "CPID-001");
        assertThat(token).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("different cpids yield different tokens within a dataset")
    void differentCpidsDiffer() {
        assertThat(tokeniser.subjectToken("dataset-a-secret-ref", "CPID-001"))
                .isNotEqualTo(tokeniser.subjectToken("dataset-a-secret-ref", "CPID-002"));
    }

    @Test
    @DisplayName("blank cpid is rejected")
    void blankCpidRejected() {
        assertThatThrownBy(() -> tokeniser.subjectToken("dataset-a-secret-ref", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokeniser.subjectToken("dataset-a-secret-ref", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("unresolvable secret reference fails closed")
    void unresolvableSecretFailsClosed() {
        assertThatThrownBy(() -> tokeniser.subjectToken("no-such-ref", "CPID-001"))
                .isInstanceOf(IllegalStateException.class);
    }
}
