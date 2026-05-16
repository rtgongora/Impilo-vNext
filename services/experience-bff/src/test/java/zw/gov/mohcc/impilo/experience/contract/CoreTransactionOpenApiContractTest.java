package zw.gov.mohcc.impilo.experience.contract;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTransactionOpenApiContractTest {

    private static Path findRepoRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            Path contractsDir = current.resolve("contracts").resolve("openapi");
            if (Files.isDirectory(contractsDir)) {
                return current;
            }
            current = current.getParent();
        }
        return start.toAbsolutePath().normalize();
    }

    @Test
    void coreTransactionContractContainsDualSurfacePaths() throws Exception {
        Path repoRoot = findRepoRoot(Path.of(""));
        Path focusedSpec = repoRoot.resolve("contracts/openapi/core-transaction-openapi.yaml");
        Path bffSpec = repoRoot.resolve("contracts/openapi/experience-bff.openapi.yaml");

        assertTrue(Files.exists(focusedSpec), "Missing OpenAPI spec: " + focusedSpec);
        assertTrue(Files.exists(bffSpec), "Missing OpenAPI spec: " + bffSpec);

        String focused = Files.readString(focusedSpec, StandardCharsets.UTF_8);
        String bff = Files.readString(bffSpec, StandardCharsets.UTF_8);

        assertTrue(focused.contains("/experience/core-transactions:"), "Missing /experience/core-transactions in focused spec");
        assertTrue(focused.contains("/experience/core-transactions/{transactionId}:"), "Missing /experience/core-transactions/{transactionId} in focused spec");
        assertTrue(focused.contains("/experience/core-transactions/{transactionId}/timeline:"), "Missing timeline endpoint in focused spec");
        assertTrue(focused.contains("/experience/core-transactions/{transactionId}/next-actions:"), "Missing next-actions endpoint in focused spec");
        assertTrue(focused.contains("/experience/core-transactions/{transactionId}/journeys:"), "Missing journeys endpoint in focused spec");
        assertTrue(focused.contains("/experience/core-transactions/{transactionId}/nompilo:"), "Missing nompilo endpoint in focused spec");
        assertTrue(focused.contains("/experience/core-transactions/{transactionId}/feedback:"), "Missing feedback endpoint in focused spec");
        assertTrue(focused.contains("/experience/core-transactions/{transactionId}/nompilo/handoff:"), "Missing nompilo handoff endpoint in focused spec");
        assertTrue(focused.contains("/experience/core-transactions/{transactionId}/nompilo/command:"), "Missing nompilo command endpoint in focused spec");
        assertTrue(focused.contains("blockerReasons:"), "Missing blockerReasons schema in focused spec");
        assertTrue(focused.contains("nompiloCommand:"), "Missing nompiloCommand schema in focused spec");
        assertTrue(focused.contains("CoreTransactionJourneyView"), "Missing journey schema in focused spec");

        assertTrue(bff.contains("/internal/v1/core-transactions:"), "Missing /internal/v1/core-transactions in experience-bff spec");
        assertTrue(bff.contains("/internal/v1/core-transactions/{transactionId}:"), "Missing /internal/v1/core-transactions/{transactionId} in experience-bff spec");
        assertTrue(bff.contains("/internal/v1/core-transactions/{transactionId}/journeys:"), "Missing /journeys in experience-bff spec");
        assertTrue(bff.contains("/internal/v1/core-transactions/{transactionId}/nompilo:"), "Missing /nompilo in experience-bff spec");
        assertTrue(bff.contains("/internal/v1/core-transactions/{transactionId}/feedback:"), "Missing /feedback in experience-bff spec");
        assertTrue(bff.contains("/internal/v1/core-transactions/{transactionId}/nompilo/handoff:"), "Missing /nompilo/handoff in experience-bff spec");
        assertTrue(bff.contains("/internal/v1/core-transactions/{transactionId}/nompilo/command:"), "Missing /nompilo/command in experience-bff spec");
        assertTrue(bff.contains("CoreTransactionBffView"), "Missing explicit core transaction schema in experience-bff spec");
        assertTrue(bff.contains("blockerReasons:"), "Missing blockerReasons schema in experience-bff spec");
        assertTrue(bff.contains("nompiloCommand:"), "Missing nompiloCommand schema in experience-bff spec");
    }

    @Test
    void coreTransactionSchemaParity_containsCriticalFieldsInBothSpecs() throws Exception {
        Path repoRoot = findRepoRoot(Path.of(""));
        Path focusedSpec = repoRoot.resolve("contracts/openapi/core-transaction-openapi.yaml");
        Path bffSpec = repoRoot.resolve("contracts/openapi/experience-bff.openapi.yaml");

        String focused = Files.readString(focusedSpec, StandardCharsets.UTF_8);
        String bff = Files.readString(bffSpec, StandardCharsets.UTF_8);

        String[] criticalSchemaTokens = new String[] {
                "CoreTransactionBffView",
                "CoreTransactionJourneyView",
                "NompiloCompanionContext",
                "failureModes:",
                "blockerReasons:",
                "journeys:",
                "nompilo:",
                "nompiloCommand:"
        };

        for (String token : criticalSchemaTokens) {
            assertTrue(focused.contains(token), "Focused spec missing token: " + token);
            assertTrue(bff.contains(token), "Experience BFF spec missing token: " + token);
        }
    }
}
