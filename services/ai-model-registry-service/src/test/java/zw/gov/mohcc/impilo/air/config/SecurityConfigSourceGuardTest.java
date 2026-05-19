package zw.gov.mohcc.impilo.air.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigSourceGuardTest {

    @Test
    void productionSecurityDoesNotPermitInternalProbeRoutes() throws Exception {
        String source = Files.readString(Path.of("src/main/java/zw/gov/mohcc/impilo/air/config/SecurityConfig.java"));

        assertFalse(source.contains("\"/internal/v1/health\","));
        assertFalse(source.contains("\"/internal/v1/test-command\""));
        assertTrue(source.contains(".anyRequest()"));
        assertTrue(source.contains(".authenticated()"));
    }
}
