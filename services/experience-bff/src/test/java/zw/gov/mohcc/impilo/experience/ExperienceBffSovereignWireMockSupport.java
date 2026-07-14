package zw.gov.mohcc.impilo.experience;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * In-process stubs for sovereign HTTP dependencies used by JVM integration tests
 * (pharmacy-service, TUSO staffing, marketplace) so CI does not require live services.
 */
public final class ExperienceBffSovereignWireMockSupport {

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());
    private static volatile boolean started;

    private ExperienceBffSovereignWireMockSupport() {}

    public static void register(DynamicPropertyRegistry registry) {
        synchronized (SERVER) {
            if (!started) {
                SERVER.start();
                stubPharmacy();
                stubTusoStaffing();
                stubMarketplace();
                stubSupport();
                stubPct();
                stubInpatientBeds();
                started = true;
            }
        }
        String base = "http://localhost:" + SERVER.port();
        registry.add("impilo.services.pharmacy-base-url", () -> base);
        registry.add("impilo.services.tuso-base-url", () -> base);
        registry.add("impilo.services.msika-flow-base-url", () -> base);
        registry.add("impilo.services.support-base-url", () -> base);
        registry.add("impilo.services.pct-base-url", () -> base);
        registry.add("impilo.services.inpatient-base-url", () -> base);
    }

    private static void stubPharmacy() {
        SERVER.stubFor(get(urlPathMatching("/v1/prescriptions/patient/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));
    }

    private static void stubTusoStaffing() {
        SERVER.stubFor(get(urlPathMatching("/v1/staffing/roster-week.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));

        SERVER.stubFor(get(urlPathMatching("/v1/staffing/on-call/swaps.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":[{"type":"OnCallSwap","id":"swap-seed-1",\
                                "attributes":{"status":"PENDING"}}]}\
                                """)));

        SERVER.stubFor(post(urlPathMatching("/v1/staffing/on-call/swaps.*"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":{"type":"OnCallSwap","id":"a1b2c3d4-0001-4000-8000-000000000099",\
                                "attributes":{"status":"PENDING"}}}\
                                """)));

        SERVER.stubFor(post(urlPathMatching("/v1/staffing/on-call/swaps/[^/]+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":{"type":"OnCallSwap","id":"a1b2c3d4-0001-4000-8000-000000000099",\
                                "attributes":{"status":"APPROVED"}}}\
                                """)));

        SERVER.stubFor(get(urlPathMatching("/v1/staffing/on-call"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":[{"type":"OnCallAssignment","id":"oncall-1",\
                                "attributes":{"specialty":"General"}}]}\
                                """)));
    }

    private static void stubMarketplace() {
        SERVER.stubFor(post(urlPathMatching("/v1/orders"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"type":"MarketplaceOrder","id":"ord-wm-1",\
                                "attributes":{"status":"SUBMITTED"}}\
                                """)));
    }

    private static void stubPct() {
        SERVER.stubFor(post(urlPathMatching("/v1/journeys/.*/encounter/start"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"type":"Encounter","id":"42",\
                                "attributes":{"status":"IN_PROGRESS"}}\
                                """)));

        SERVER.stubFor(post(urlPathMatching("/v1/encounters/.*/complete"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"type":"Encounter","id":"42",\
                                "attributes":{"status":"COMPLETED"}}\
                                """)));

        SERVER.stubFor(get(urlPathMatching("/v1/patient/.*/timeline"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));
    }

    private static void stubSupport() {
        SERVER.stubFor(get(urlPathMatching("/internal/v1/support/tickets.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));
    }

    /**
     * Inpatient-service bed/ward management. The BFF {@code BedController} proxies
     * {@code GET /internal/v1/beds/wards?facility_id=…} to inpatient-service via
     * {@code InpatientServiceClient.listWards}; without this stub the call reaches the
     * default {@code localhost:8121}, is refused, and the controller correctly surfaces
     * a 502 {@code INPATIENT_UNAVAILABLE}. The payload mirrors
     * {@code BedManagementService.listWardResources}: a JSON:API-style
     * {@code {"data":[{id,type:"ward",attributes:{…}}]}} envelope.
     */
    private static void stubInpatientBeds() {
        SERVER.stubFor(get(urlPathEqualTo("/internal/v1/beds/wards"))
                .withQueryParam("facility_id", matching("[0-9a-fA-F-]{36}"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":[{"id":"11111111-2222-4333-8444-555555555555",\
                                "type":"ward","attributes":{"name":"General Ward A",\
                                "facilityId":"00000000-0000-0000-0000-000000000001",\
                                "wardType":"GENERAL","totalBeds":24,"occupiedBeds":18,\
                                "availableBeds":5,"maintenanceBeds":1,"genderDesignation":"MIXED",\
                                "ageGroup":"ADULT","isolationCapable":false,"oxygenAvailable":true,\
                                "monitoringCapable":true,"icuCapable":false}}]}\
                                """)));
    }
}
