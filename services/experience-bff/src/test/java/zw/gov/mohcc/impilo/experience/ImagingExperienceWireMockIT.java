package zw.gov.mohcc.impilo.experience;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration slice: Experience BFF governed imaging list against a WireMock PACS adapter and
 * synthetic TSHEPO authorize, with Testcontainers Redis (or external Redis) like other BFF ITs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(DockerOrExternalPostgresCondition.class)
class ImagingExperienceWireMockIT {

    private static final WireMockServer WIRE_MOCK =
            new WireMockServer(wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void registerBaseUrls(DynamicPropertyRegistry registry) {
        ExperienceBffTestRedisSupport.configure(ImagingExperienceWireMockIT.class, registry);
        synchronized (WIRE_MOCK) {
            if (!WIRE_MOCK.isRunning()) {
                WIRE_MOCK.start();
            }
        }
        String base = "http://localhost:" + WIRE_MOCK.port();
        registry.add("impilo.services.pacs-base-url", () -> base);
        registry.add("impilo.services.tshepo-authz-base-url", () -> base);
        registry.add("impilo.imaging.require-tshepo-authorize", () -> "true");
        registry.add("impilo.imaging.audit-ingest-enabled", () -> "false");
    }

    @BeforeAll
    static void installStubs() {
        String studyRow =
                "[{\"id\":1,\"tenantId\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"patientCpid\":\"CPID-IT-1\",\"studyUid\":\"1.2.3.4.5.6.7.8.9\","
                        + "\"modality\":\"CT\",\"studyDate\":\"2026-01-15T10:00:00Z\",\"status\":\"RECEIVED\"}]";
        WIRE_MOCK.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/internal/v1/imaging-studies"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(studyRow)));
        WIRE_MOCK.stubFor(
                post(urlEqualTo("/v1/authorize"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody("{\"verdict\":\"ALLOW\"}")));
    }

    @AfterAll
    static void shutdown() {
        WIRE_MOCK.stop();
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void listImagingStudies_proxiesPacsAndHonorsGovernanceEnvelope() throws Exception {
        mvc.perform(
                        get("/internal/v1/imaging/studies")
                                .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_CLINICIAN"))
                                .jwt(j -> j.claim("realm_access", Map.of("roles", List.of("CLINICIAN")))))
                                .header("X-Tenant-ID", "00000000-0000-0000-0000-000000000001")
                                .header("X-Pod-ID", "national-spine")
                                .header("X-Request-ID", UUID.randomUUID().toString())
                                .header("X-Correlation-ID", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[0].studyUid").value("1.2.3.4.5.6.7.8.9"))
                .andExpect(jsonPath("$.data[0].patientCpid").value("CPID-IT-1"));
    }
}
