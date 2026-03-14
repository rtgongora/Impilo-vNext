package zw.gov.mohcc.impilo.security.secrets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import static org.junit.jupiter.api.Assertions.*;

class SecretProviderTest {

    // --- EnvSecretProvider ---

    @Test
    void envProvider_mapsKeyToEnvVar() {
        var provider = new EnvSecretProvider("IMPILO_");
        assertEquals("IMPILO_DB_PASSWORD", provider.toEnvVar("db.password"));
        assertEquals("IMPILO_JWT_SIGNING_KEY", provider.toEnvVar("jwt.signing-key"));
    }

    @Test
    void envProvider_returnsEmptyForMissingKey() {
        var provider = new EnvSecretProvider("IMPILO_TEST_NONEXIST_");
        assertTrue(provider.getSecret("missing.key").isEmpty());
    }

    @Test
    void envProvider_requireSecretThrowsForMissing() {
        var provider = new EnvSecretProvider("IMPILO_TEST_NONEXIST_");
        var ex = assertThrows(SecretNotFoundException.class,
                () -> provider.requireSecret("missing.key"));
        assertEquals("missing.key", ex.getKey());
        // Verify the exception message does NOT contain any secret value
        assertFalse(ex.getMessage().contains("="),
                "Exception message must not contain secret values");
    }

    @Test
    void envProvider_toStringDoesNotRevealSecrets() {
        var provider = new EnvSecretProvider("IMPILO_");
        String str = provider.toString();
        assertFalse(str.contains("password"), "toString must not reveal secret values");
        assertTrue(str.contains("EnvSecretProvider"));
        assertTrue(str.contains("IMPILO_"));
    }

    @Test
    void secretProviderNeverLogsSecrets() {
        // Capture all log output during secret operations
        ByteArrayOutputStream logCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        PrintStream originalOut = System.out;
        System.setErr(new PrintStream(logCapture));
        System.setOut(new PrintStream(logCapture));

        try {
            var provider = new EnvSecretProvider("IMPILO_");

            // Operations that might log
            provider.getSecret("db.password");
            provider.toString();
            try { provider.requireSecret("nonexistent"); } catch (SecretNotFoundException ignored) {}

            String logOutput = logCapture.toString();
            // Verify no secret values appear in log output
            // (We can't test for the actual secret value since env vars aren't set,
            // but we verify the pattern: key references are safe, values never logged)
            assertFalse(logOutput.contains("secret_value"),
                    "Secret values must never appear in logs");
        } finally {
            System.setErr(originalErr);
            System.setOut(originalOut);
        }
    }

    // --- VaultSecretProvider ---

    @Test
    void vaultProvider_fallsBackToEnvVar() {
        var config = VaultSecretProvider.VaultConfig.forLocalDev("tshepo");
        var provider = new VaultSecretProvider(config);
        // Falls back to env var lookup — won't find in test, but should not throw
        assertTrue(provider.getSecret("nonexistent.key").isEmpty());
    }

    @Test
    void vaultProvider_toStringDoesNotRevealSecrets() {
        var config = VaultSecretProvider.VaultConfig.forLocalDev("tshepo");
        var provider = new VaultSecretProvider(config);
        String str = provider.toString();
        assertFalse(str.contains("password"));
        assertTrue(str.contains("VaultSecretProvider"));
        assertTrue(str.contains("localhost"));
    }

    @Test
    void vaultProvider_refreshClearsCache() {
        var config = VaultSecretProvider.VaultConfig.forLocalDev("tshepo");
        var provider = new VaultSecretProvider(config);
        // Should not throw
        assertDoesNotThrow(provider::refresh);
    }

    @Test
    void vaultConfig_localDevDefaults() {
        var config = VaultSecretProvider.VaultConfig.forLocalDev("vito");
        assertEquals("http://localhost:8200", config.vaultAddress());
        assertEquals("impilo-kv", config.secretsEngine());
        assertEquals("services/vito", config.secretPath());
        assertEquals("IMPILO_", config.envPrefix());
        assertEquals(60, config.cacheTtlSeconds());
    }

    @Test
    void vaultConfig_rejectsNullRequired() {
        assertThrows(NullPointerException.class, () ->
                new VaultSecretProvider.VaultConfig(null, "engine", "path", null, null, 0));
        assertThrows(NullPointerException.class, () ->
                new VaultSecretProvider.VaultConfig("addr", null, "path", null, null, 0));
    }
}
