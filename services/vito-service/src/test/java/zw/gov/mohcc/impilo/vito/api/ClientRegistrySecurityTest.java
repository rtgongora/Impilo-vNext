package zw.gov.mohcc.impilo.vito.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.vito.config.SecurityConfig;
import zw.gov.mohcc.impilo.vito.core.ClientIdentityOperationsService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClientIdentityOperationsController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ClientRegistrySecurityTest {

    private static final String TENANT_ID = UUID.randomUUID().toString();
    private static final String CORRELATION_ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientIdentityOperationsService clientIdentityOperationsService;

    @Test
    void listClients_allowsTrustedInternalCallWhenPreviewBypassEnabled() throws Exception {
        when(clientIdentityOperationsService.searchClients(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/client-registry/clients")
                        .header(TrustContext.H_TENANT_ID, TENANT_ID)
                        .header(TrustContext.H_CORRELATION_ID, CORRELATION_ID)
                        .header(TrustContext.H_ACCESS_MODE, "INTERNAL")
                        .header("X-Service-Id", "experience-bff"))
                .andExpect(status().isOk());
    }
}
