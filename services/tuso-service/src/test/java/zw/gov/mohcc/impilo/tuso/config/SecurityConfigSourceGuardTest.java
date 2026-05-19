package zw.gov.mohcc.impilo.tuso.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigSourceGuardTest {

    @Test
    void securityConfigMustNotPermitAllOnAnyRequest() throws Exception {
        String text = Files.readString(Path.of("src", "main", "java", "zw", "gov", "mohcc", "impilo", "tuso", "config", "SecurityConfig.java"));
        assertThat(text).contains(".anyRequest().authenticated()");
        assertThat(text).doesNotContain(".anyRequest().permitAll()");
        assertThat(text).contains("@Value(\"${impilo.security.disable-oauth-for-tests:false}\")");
        assertThat(text).contains("requestMatchers(disableOauthForTests ? \"/v1/**\" : \"/__disabled_test_auth_bypass__\").permitAll()");
    }
}
