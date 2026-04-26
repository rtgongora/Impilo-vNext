package zw.gov.mohcc.impilo.experience.contract;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal contract gate for Tier-2 provider mobile endpoints.
 *
 * Ensures the OpenAPI file exists and contains the key Tier-2 paths that the
 * provider mobile app relies on for EHR/Queue/Lab/Pharmacy/Marketplace waves.
 */
public class MobileProviderOpenApiContractTest {

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
    void mobileProviderOpenApiContainsTier2Paths() throws Exception {
        Path repoRoot = findRepoRoot(Path.of(""));
        Path spec = repoRoot.resolve("contracts/openapi/mobile-provider.openapi.yaml");
        assertTrue(Files.exists(spec), "Missing OpenAPI spec: " + spec);

        String text = Files.readString(spec, StandardCharsets.UTF_8);
        assertTrue(text.contains("openapi:"), "Spec does not look like OpenAPI");

        // Tier-3 ops foundation (facility/workspace context)
        assertTrue(text.contains("/internal/v1/facilities:"), "Missing facilities list path");
        assertTrue(text.contains("/internal/v1/facilities/{id}:"), "Missing facility detail path");
        assertTrue(text.contains("/internal/v1/workspaces:"), "Missing workspaces list path");

        // Queue
        assertTrue(text.contains("/internal/v1/mobile/provider/queue:"), "Missing provider queue path");
        assertTrue(text.contains("/internal/v1/mobile/provider/queue/call-next:"), "Missing provider queue call-next path");
        assertTrue(text.contains("/internal/v1/mobile/provider/queue/stats:"), "Missing provider queue stats path");

        // Lab
        assertTrue(text.contains("/internal/v1/mobile/provider/labs:"), "Missing provider labs path");
        assertTrue(text.contains("/internal/v1/mobile/provider/labs/results:"), "Missing provider labs results path");

        // Pharmacy
        assertTrue(text.contains("/internal/v1/mobile/provider/pharmacy/pending:"), "Missing provider pharmacy pending path");
        assertTrue(text.contains("/internal/v1/mobile/provider/pharmacy/dispense:"), "Missing provider pharmacy dispense path");
        assertTrue(text.contains("/internal/v1/mobile/provider/pharmacy/verify-five-rights:"), "Missing provider pharmacy verify-five-rights path");

        // Tier-3 wave 3 (admin + registry hub)
        assertTrue(text.contains("/internal/v1/mobile/provider/admin-registry/hub:"), "Missing provider admin-registry hub path");

        // Tier-3 wave 4 (operations + reports hub)
        assertTrue(text.contains("/internal/v1/mobile/provider/ops-reports/hub:"), "Missing provider ops-reports hub path");
        assertTrue(text.contains("/internal/v1/mobile/provider/reports:"), "Missing provider reports list path");
        assertTrue(text.contains("/internal/v1/mobile/provider/reports/{reportId}/data:"), "Missing provider report data path");

        // Tier-3 wave 5 (developer portal hub) — Gap-2: same typed envelope as other professional hubs
        assertTrue(text.contains("/internal/v1/mobile/provider/developer/hub:"), "Missing provider developer hub path");
        int developerHubIdx = text.indexOf("/internal/v1/mobile/provider/developer/hub:");
        assertTrue(developerHubIdx >= 0, "Developer hub path anchor missing");
        String developerHubSlice = text.substring(developerHubIdx, Math.min(text.length(), developerHubIdx + 900));
        assertTrue(
                developerHubSlice.contains("ProviderMobileHubResponse"),
                "Developer hub response should reference ProviderMobileHubResponse (typed hub envelope)");

        // Tier-3 wave 6 (professional settings hub)
        assertTrue(text.contains("/internal/v1/mobile/provider/professional-settings/hub:"), "Missing provider professional-settings hub path");

        // Tier-3 wave 7 (omnichannel, coverage, credentials, public-health drill-down)
        assertTrue(text.contains("/internal/v1/mobile/provider/professional-channels/hub:"), "Missing provider professional-channels hub path");

        // Gap-1 strict schemas (typed hub + ops foundation envelopes)
        assertTrue(text.contains("ProviderMobileHubResponse"), "Missing ProviderMobileHubResponse schema component");
        assertTrue(text.contains("ProviderHubPayload"), "Missing ProviderHubPayload schema component");
        assertTrue(text.contains("ProviderFacilityListResponse"), "Missing ProviderFacilityListResponse schema component");
        assertTrue(text.contains("ProviderWorkspaceListResponse"), "Missing ProviderWorkspaceListResponse schema component");

        // Tier-2 strict envelopes (queue / pharmacy / triage / labs)
        assertTrue(text.contains("ProviderQueueListResponse"), "Missing ProviderQueueListResponse schema component");
        assertTrue(text.contains("ProviderQueueStatsResponse"), "Missing ProviderQueueStatsResponse schema component");
        assertTrue(text.contains("ProviderQueueCallNextResponse"), "Missing ProviderQueueCallNextResponse schema component");
        assertTrue(text.contains("ProviderQueueCompleteResponse"), "Missing ProviderQueueCompleteResponse schema component");
        assertTrue(text.contains("ProviderPharmacyPendingResponse"), "Missing ProviderPharmacyPendingResponse schema component");
        assertTrue(text.contains("ProviderPharmacyVerifyFiveRightsResponse"), "Missing ProviderPharmacyVerifyFiveRightsResponse schema component");
        assertTrue(text.contains("ProviderPharmacyDispenseResponse"), "Missing ProviderPharmacyDispenseResponse schema component");
        assertTrue(text.contains("ProviderTriageListResponse"), "Missing ProviderTriageListResponse schema component");
        assertTrue(text.contains("ProviderTriageRecordRequest"), "Missing ProviderTriageRecordRequest schema component");
        assertTrue(text.contains("ProviderLabOrderCreatedResponse"), "Missing ProviderLabOrderCreatedResponse schema component");
        assertTrue(text.contains("ProviderLabPassthroughResponse"), "Missing ProviderLabPassthroughResponse schema component");

        // Gap 3 vertical: reports (typed list + detail)
        assertTrue(text.contains("ProviderMobileReportListResponse"), "Missing ProviderMobileReportListResponse schema component");
        assertTrue(text.contains("ProviderMobileReportDataResponse"), "Missing ProviderMobileReportDataResponse schema component");
    }
}

