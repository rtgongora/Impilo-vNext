package zw.gov.mohcc.impilo.coverage.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigSourceGuardTest {

    @Test
    void productionSecurityMustNotPermitAllOnAnyRequest() throws Exception {
        Path source = Path.of("src", "main", "java", "zw", "gov", "mohcc", "impilo", "coverage", "config", "SecurityConfig.java");
        String text = Files.readString(source);
        assertThat(text).contains("@Profile(\"!test\")");
        assertThat(text).contains(".anyRequest()");
        assertThat(text).contains(".authenticated())");
    }
}
