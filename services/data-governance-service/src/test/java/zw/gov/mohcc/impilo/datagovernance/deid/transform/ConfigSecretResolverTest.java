package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConfigSecretResolver — config/env-backed secret references (production custody: tshepo-keys)")
class ConfigSecretResolverTest {

    @Test
    @DisplayName("resolves a configured secret reference")
    void resolvesConfiguredReference() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("impilo.deid.secrets.my-dataset-ref", "material");
        assertThat(new ConfigSecretResolver(environment).resolve("my-dataset-ref"))
                .isEqualTo("material");
    }

    @Test
    @DisplayName("missing reference fails closed")
    void missingReferenceFailsClosed() {
        ConfigSecretResolver resolver = new ConfigSecretResolver(new MockEnvironment());
        assertThatThrownBy(() -> resolver.resolve("absent-ref"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tshepo-keys");
    }

    @Test
    @DisplayName("blank secret material fails closed")
    void blankMaterialFailsClosed() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("impilo.deid.secrets.blank-ref", "");
        assertThatThrownBy(() -> new ConfigSecretResolver(environment).resolve("blank-ref"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("blank reference fails closed")
    void blankReferenceFailsClosed() {
        ConfigSecretResolver resolver = new ConfigSecretResolver(new MockEnvironment());
        assertThatThrownBy(() -> resolver.resolve(" "))
                .isInstanceOf(IllegalStateException.class);
    }
}
