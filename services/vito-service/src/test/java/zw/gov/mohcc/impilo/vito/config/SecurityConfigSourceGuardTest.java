package zw.gov.mohcc.impilo.vito.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigSourceGuardTest {

    @Test
    void productionSecurityMustAuthenticateBusinessApis() throws Exception {
        String text = Files.readString(Path.of(
                "src", "main", "java", "zw", "gov", "mohcc", "impilo", "vito", "config", "SecurityConfig.java"));
        int prodStart = text.indexOf("productionFilterChain");
        String productionSection = prodStart >= 0 ? text.substring(prodStart) : text;

        assertThat(productionSection).contains(".anyRequest().authenticated()");
        assertThat(productionSection).doesNotContain(".anyRequest().permitAll()");
    }

    @Test
    void previewBypassMustBeFlagGatedAndScopedToClientRegistry() throws Exception {
        String text = Files.readString(Path.of(
                "src", "main", "java", "zw", "gov", "mohcc", "impilo", "vito", "config", "SecurityConfig.java"));

        assertThat(text).contains("@ConditionalOnProperty(name = \"impilo.security.disable-oauth-for-tests\", havingValue = \"true\")");
        assertThat(text).contains(".requestMatchers(\"/v1/client-registry/**\").permitAll()");
        assertThat(text).contains("testFilterChain");
        assertThat(text).contains("productionFilterChain");
    }
}
