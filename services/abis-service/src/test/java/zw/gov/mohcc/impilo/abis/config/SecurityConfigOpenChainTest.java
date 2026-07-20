package zw.gov.mohcc.impilo.abis.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Local-Compose chain ({@code impilo.security.oauth2.enabled=false}): OAuth is off,
 * but the {@code TrustContextFilter} still runs — trust headers keep flowing to
 * matching/adjudication/audit paths, so tenant scoping stays fail-closed.
 */
@WebMvcTest(controllers = AbisStatsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "impilo.security.oauth2.enabled=false")
class SecurityConfigOpenChainTest {

    private static final String TENANT = "10000000-4000-8000-9000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AbisStatsService statsService;

    @Test
    void openChainStillPopulatesTrustContext() throws Exception {
        AtomicReference<TrustContext> seen = new AtomicReference<>();
        when(statsService.stats(any())).thenAnswer(inv -> {
            seen.set(TrustContextHolder.get());
            return Map.of("templates", 0);
        });

        mockMvc.perform(get("/v1/abis/stats").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk());

        assertThat(seen.get())
                .as("TrustContextFilter must be registered in the open chain too")
                .isNotNull();
        assertThat(seen.get().tenantId()).isEqualTo(UUID.fromString(TENANT));
    }
}
