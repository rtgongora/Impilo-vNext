package zw.gov.mohcc.impilo.productregistry.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigSourceGuardTest {

    @Test
    void securityConfigMustNotPermitAllOnAnyRequest() throws Exception {
        String text = Files.readString(Path.of("src", "main", "java", "zw", "gov", "mohcc", "impilo", "productregistry", "config", "SecurityConfig.java"));
        assertThat(text).contains(".anyRequest().authenticated()");
        assertThat(text).doesNotContain(".anyRequest().permitAll()");
    }
}
