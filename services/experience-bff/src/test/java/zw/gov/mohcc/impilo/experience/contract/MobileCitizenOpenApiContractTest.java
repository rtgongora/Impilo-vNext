package zw.gov.mohcc.impilo.experience.contract;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal contract gate for citizen mobile OpenAPI.
 *
 * Tier-3 wave 1: citizen facility directory + facility detail.
 */
public class MobileCitizenOpenApiContractTest {

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
    void mobileCitizenOpenApiContainsFacilityPaths() throws Exception {
        Path repoRoot = findRepoRoot(Path.of(""));
        Path spec = repoRoot.resolve("contracts/openapi/mobile-citizen.openapi.yaml");
        assertTrue(Files.exists(spec), "Missing OpenAPI spec: " + spec);

        String text = Files.readString(spec, StandardCharsets.UTF_8);
        assertTrue(text.contains("openapi:"), "Spec does not look like OpenAPI");

        assertTrue(text.contains("/internal/v1/facilities:"), "Missing facilities list path");
        assertTrue(text.contains("/internal/v1/facilities/{id}:"), "Missing facility detail path");

        // Tier-3 wave 2 long-tail
        assertTrue(text.contains("/internal/v1/mobile/citizen/share/issue:"), "Missing share issue path");
        assertTrue(text.contains("/internal/v1/mobile/citizen/share/claim:"), "Missing share claim path");
        assertTrue(text.contains("/internal/v1/mobile/citizen/credential/verify:"), "Missing credential verify path");
        assertTrue(text.contains("/internal/v1/mobile/citizen/delegated-pickup/issue:"), "Missing delegated pickup issue path");

        assertTrue(text.contains("CitizenFacilityListResponse"), "Missing CitizenFacilityListResponse schema component");
        assertTrue(text.contains("CitizenFacilityDetailResponse"), "Missing CitizenFacilityDetailResponse schema component");
        assertTrue(text.contains("CitizenShareIssueResponse"), "Missing CitizenShareIssueResponse schema component");
        assertTrue(text.contains("CitizenShareClaimResponse"), "Missing CitizenShareClaimResponse schema component");
        assertTrue(text.contains("CitizenCredentialVerifyResponse"), "Missing CitizenCredentialVerifyResponse schema component");
        assertTrue(text.contains("CitizenDelegatedPickupIssueResponse"), "Missing CitizenDelegatedPickupIssueResponse schema component");
    }
}

