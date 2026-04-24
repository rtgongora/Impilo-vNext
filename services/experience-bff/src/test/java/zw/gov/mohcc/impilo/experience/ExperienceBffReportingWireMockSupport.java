package zw.gov.mohcc.impilo.experience;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * In-process reporting-service stub so {@code POST /internal/v1/reports/generate} integration
 * tests do not require a live service on {@code localhost:8176}.
 */
public final class ExperienceBffReportingWireMockSupport {

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());
    private static volatile boolean started;

    private ExperienceBffReportingWireMockSupport() {}

    public static void register(DynamicPropertyRegistry registry) {
        synchronized (SERVER) {
            if (!started) {
                SERVER.start();
                SERVER.stubFor(post(urlEqualTo("/internal/v1/reports"))
                        .willReturn(aResponse()
                                .withStatus(201)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        "{\"data\":{\"type\":\"ReportJob\",\"id\":\"wm-job-1\","
                                                + "\"attributes\":{\"status\":\"QUEUED\"}}}")));
                started = true;
            }
        }
        registry.add("impilo.services.reporting-base-url", () -> "http://localhost:" + SERVER.port());
    }
}
