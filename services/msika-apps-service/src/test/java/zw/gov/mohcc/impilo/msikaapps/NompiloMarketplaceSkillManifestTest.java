package zw.gov.mohcc.impilo.msikaapps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural validation for the Nompilo Marketplace Helper AI Skill manifest
 * published in {@code contracts/ai-skills/nompilo-marketplace-helper.skill.json}.
 *
 * <p>This test asserts the manifest parses, declares the expected stable tool
 * names, names the correct Java implementation class, and respects the
 * non-autonomy doctrine (no prohibited actions claim a tool, no tool has a
 * write effect).</p>
 */
class NompiloMarketplaceSkillManifestTest {

    private static final List<String> EXPECTED_TOOLS = List.of(
            "findMarketplaceCapability",
            "explainCapabilityStatus",
            "listInstalledCapabilities",
            "summarizePendingApprovals",
            "troubleshootIntegration"
    );

    private static final String EXPECTED_IMPL_CLASS =
            "zw.gov.mohcc.impilo.msikaapps.nompilo.NompiloMarketplaceTools";

    @Test
    @DisplayName("Manifest parses and declares the expected stable tool surface")
    void manifestParsesAndExposesExpectedTools() throws IOException {
        JsonNode manifest = loadManifest();

        assertThat(manifest.path("itemCode").asText()).isEqualTo("nompilo-marketplace-helper");
        assertThat(manifest.path("version").asText()).matches("^[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9.-]+)?$");
        assertThat(manifest.path("tshepoPolicyRef").asText()).isNotBlank();
        assertThat(manifest.path("phiAccess").asBoolean(true)).isFalse();

        JsonNode tools = manifest.path("tools");
        assertThat(tools.isArray()).isTrue();
        List<String> toolNames = new ArrayList<>();
        tools.forEach(t -> toolNames.add(t.path("name").asText()));
        assertThat(toolNames).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS);

        for (JsonNode t : tools) {
            assertThat(t.path("implementedBy").path("className").asText())
                    .as("tool " + t.path("name").asText() + " is implemented by NompiloMarketplaceTools")
                    .isEqualTo(EXPECTED_IMPL_CLASS);
            assertThat(t.path("implementedBy").path("serviceId").asText()).isEqualTo("msika-apps-service");
            assertThat(t.path("effect").asText())
                    .as("non-autonomy doctrine: marketplace helper tools must be READ_ONLY")
                    .isEqualTo("READ_ONLY");
            assertThat(t.path("requiredScopes").size()).isGreaterThan(0);
            assertThat(t.path("rolesAllowed").size()).isGreaterThan(0);
        }

        JsonNode prohibited = manifest.path("prohibitedAutonomousActions");
        assertThat(prohibited.isArray()).isTrue();
        Set<String> phrases = new HashSet<>();
        prohibited.forEach(p -> phrases.add(p.asText().toLowerCase()));
        assertThat(phrases).anyMatch(p -> p.contains("approve activation"));
        assertThat(phrases).anyMatch(p -> p.contains("suspend") || p.contains("reactivate"));
        assertThat(phrases).anyMatch(p -> p.contains("credential"));
    }

    private static JsonNode loadManifest() throws IOException {
        // The manifest lives at repo-root/contracts/ai-skills/... We resolve it
        // relative to the module by walking up from the service working dir.
        Path candidate = Paths.get("..", "..", "contracts", "ai-skills", "nompilo-marketplace-helper.skill.json")
                .toAbsolutePath().normalize();
        if (Files.exists(candidate)) {
            return new ObjectMapper().readTree(candidate.toFile());
        }
        // Fallback: search from current working dir up to 5 levels.
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path c = cur.resolve("contracts").resolve("ai-skills").resolve("nompilo-marketplace-helper.skill.json");
            if (Files.exists(c)) {
                return new ObjectMapper().readTree(c.toFile());
            }
            cur = cur.getParent();
            if (cur == null) break;
        }
        // Last resort: load from classpath if test resources copy it later.
        try (InputStream in = NompiloMarketplaceSkillManifestTest.class.getClassLoader()
                .getResourceAsStream("ai-skills/nompilo-marketplace-helper.skill.json")) {
            if (in != null) {
                return new ObjectMapper().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        throw new IOException("Could not locate nompilo-marketplace-helper.skill.json on disk or classpath");
    }
}
