package zw.gov.mohcc.impilo.experience;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
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
                started = true;
            }
        }
        String base = "http://localhost:" + SERVER.port();
        registry.add("impilo.services.pharmacy-base-url", () -> base);
        registry.add("impilo.services.tuso-base-url", () -> base);
        registry.add("impilo.services.msika-flow-base-url", () -> base);
        registry.add("impilo.services.support-base-url", () -> base);
        registry.add("impilo.services.pct-base-url", () -> base);
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
}
