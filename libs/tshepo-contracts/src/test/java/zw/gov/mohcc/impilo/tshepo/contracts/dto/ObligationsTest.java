package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObligationsTest {

    @Test
    void none_returnsEmptyObligation() {
        Obligations obligations = Obligations.NONE;

        assertThat(obligations.maxScope()).isNull();
        assertThat(obligations.maskFields()).isNull();
        assertThat(obligations.loggingLevel()).isEqualTo("STANDARD");
        assertThat(obligations.consentScopeRef()).isNull();
        assertThat(obligations.visibilityProfile()).isNull();
    }

    @Test
    void withElevatedLogging_setsLevel() {
        Obligations obligations = Obligations.withElevatedLogging();

        assertThat(obligations.loggingLevel()).isEqualTo("ELEVATED");
        assertThat(obligations.maxScope()).isNull();
        assertThat(obligations.maskFields()).isNull();
        assertThat(obligations.consentScopeRef()).isNull();
        assertThat(obligations.visibilityProfile()).isNull();
    }

    @Test
    void withMasking_setsMaskFields() {
        List<String> fields = List.of("name", "dob", "nid");

        Obligations obligations = Obligations.withMasking(fields);

        assertThat(obligations.maskFields()).containsExactly("name", "dob", "nid");
        assertThat(obligations.loggingLevel()).isEqualTo("STANDARD");
        assertThat(obligations.maxScope()).isNull();
        assertThat(obligations.consentScopeRef()).isNull();
        assertThat(obligations.visibilityProfile()).isNull();
    }
}
