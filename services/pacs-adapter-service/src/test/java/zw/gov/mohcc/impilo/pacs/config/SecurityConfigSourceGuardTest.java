package zw.gov.mohcc.impilo.pacs.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigSourceGuardTest {

    @Test
    void productionSecurityMustAuthenticateBusinessApis() throws Exception {
        String text = Files.readString(Path.of(
                "src", "main", "java", "zw", "gov", "mohcc", "impilo", "pacs", "config", "SecurityConfig.java"));
        int prodStart = text.indexOf("productionFilterChain");
        String productionSection = prodStart >= 0 ? text.substring(prodStart) : text;

        assertThat(productionSection).contains(".anyRequest().authenticated()");
        assertThat(productionSection).contains(".oauth2ResourceServer");
        assertThat(productionSection).doesNotContain(".anyRequest().permitAll()");
    }
}
