package zw.gov.mohcc.impilo.tshepo.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigSourceGuardTest {

    @Test
    void legacyMonolithMustNotPermitAllOnAnyRequest() throws Exception {
        Path source = Path.of("src", "main", "java", "zw", "gov", "mohcc", "impilo", "tshepo", "config", "SecurityConfig.java");
        String text = Files.readString(source);
        assertThat(text).contains(".anyRequest().authenticated()");
        assertThat(text).doesNotContain(".anyRequest().permitAll()");
    }
}
