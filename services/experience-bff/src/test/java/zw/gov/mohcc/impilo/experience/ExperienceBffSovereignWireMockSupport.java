package zw.gov.mohcc.impilo.experience;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
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
                stubVashandiStaffing();
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
        // Staffing repointed off tuso to vashandi (V009): the BFF now calls
        // /v1/internal/vashandi/staffing/*, so vashandi must resolve to this WireMock server or the
        // calls escape to a real host and 500/empty.
        registry.add("impilo.services.vashandi-base-url", () -> base);
    }

    private static void stubPharmacy() {
        SERVER.stubFor(get(urlPathMatching("/v1/prescriptions/patient/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));
    }

    /**
     * Vashandi staffing stubs (V009). The BFF was repointed off tuso {@code /v1/staffing/*} — which
     * nothing served — to vashandi {@code /v1/internal/vashandi/staffing/*}, and the swap contract
     * moved from typed names to shift/profile ids. These stubs return vashandi's real response
     * shapes ({@code shift}, {@code on-call-assignment}, {@code shift-swap-request}) so the test
     * asserts the contract the estate actually speaks, not the retired tuso one.
     */
    private static void stubVashandiStaffing() {
        SERVER.stubFor(get(urlPathMatching("/v1/internal/vashandi/staffing/roster-week.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));

        SERVER.stubFor(get(urlPathMatching("/v1/internal/vashandi/staffing/on-call/swaps.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":[{"type":"shift-swap-request","id":"swap-seed-1",\
                                "attributes":{"status":"PENDING"}}]}\
                                """)));

        // The swap decision is a PATCH downstream (vashandi @PatchMapping), where the retired tuso
        // path used POST — matching POST here would leave the decision unstubbed.
        SERVER.stubFor(patch(urlPathMatching("/v1/internal/vashandi/staffing/on-call/swaps/[^/]+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":{"type":"shift-swap-request",\
                                "id":"a1b2c3d4-0001-4000-8000-000000000099",\
                                "attributes":{"status":"APPROVED"}}}\
                                """)));

        SERVER.stubFor(post(urlPathMatching("/v1/internal/vashandi/staffing/on-call/swaps.*"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":{"type":"shift-swap-request",\
                                "id":"a1b2c3d4-0001-4000-8000-000000000099",\
                                "attributes":{"status":"PENDING"}}}\
                                """)));

        SERVER.stubFor(get(urlPathMatching("/v1/internal/vashandi/staffing/on-call"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":[{"type":"on-call-assignment","id":"2026-04-06|General",\
                                "attributes":{"assignment_date":"2026-04-06","specialty":"General",\
                                "primary_staff_reference":"PW-004821","backup_staff_reference":null}}]}\
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
