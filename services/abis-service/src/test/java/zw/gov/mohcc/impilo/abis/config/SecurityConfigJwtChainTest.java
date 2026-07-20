package zw.gov.mohcc.impilo.abis.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.abis.api.AbisStatsController;
import zw.gov.mohcc.impilo.abis.core.AbisStatsService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Default (production) security chain: biometric endpoints are NEVER anonymously
 * reachable, and the estate-standard {@code TrustContextFilter} runs inside the
 * chain so tenant/actor trust headers (Envoy ext_authz contract) are available to
 * every handler.
 */
@WebMvcTest(controllers = AbisStatsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "impilo.security.oauth2.enabled=true")
class SecurityConfigJwtChainTest {

    private static final String TENANT = "10000000-4000-8000-9000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AbisStatsService statsService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousRequestToBiometricEndpointIsRejected() throws Exception {
        mockMvc.perform(get("/v1/abis/stats").header("X-Tenant-ID", TENANT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRequestToThresholdConfigIsRejected() throws Exception {
        mockMvc.perform(get("/v1/abis/config/thresholds"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestCarriesTrustContextFromHeaders() throws Exception {
        AtomicReference<TrustContext> seen = new AtomicReference<>();
        when(statsService.stats(any())).thenAnswer(inv -> {
            seen.set(TrustContextHolder.get());
            return Map.of("templates", 0);
        });

        mockMvc.perform(get("/v1/abis/stats")
                        .with(jwt())
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Actor-ID", "actor-123")
                        .header("X-Purpose-Of-Use", "DEDUPLICATION"))
                .andExpect(status().isOk());

        assertThat(seen.get())
                .as("TrustContextFilter must populate TrustContextHolder inside the chain")
                .isNotNull();
        assertThat(seen.get().tenantId()).isEqualTo(UUID.fromString(TENANT));
        assertThat(seen.get().actorId()).isEqualTo("actor-123");
        assertThat(seen.get().purposeOfUse()).isEqualTo("DEDUPLICATION");
    }
}
