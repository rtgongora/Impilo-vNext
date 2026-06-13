package zw.gov.mohcc.impilo.msikaapps.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceLauncherSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void launcher_allowsTrustedBffInternalCallWhenPreviewBypassEnabled() throws Exception {
        mockMvc.perform(get("/internal/v1/marketplace/launcher")
                        .header(CompanionHeaders.TENANT_ID, "tenant-preview")
                        .header(CompanionHeaders.POD_ID, "pod-preview")
                        .header(CompanionHeaders.REQUEST_ID, "req-msika-1")
                        .header(CompanionHeaders.CORRELATION_ID, "corr-msika-1")
                        .header(CompanionHeaders.ACCESS_MODE, "INTERNAL")
                        .header(CompanionHeaders.SERVICE_ID, "experience-bff"))
                .andExpect(status().isOk());
    }

    @Test
    void catalogueItems_remainProtectedWithoutRoles() throws Exception {
        mockMvc.perform(get("/internal/v1/marketplace/items")
                        .header(CompanionHeaders.TENANT_ID, "tenant-preview")
                        .header(CompanionHeaders.POD_ID, "pod-preview")
                        .header(CompanionHeaders.REQUEST_ID, "req-msika-3")
                        .header(CompanionHeaders.CORRELATION_ID, "corr-msika-3")
                        .header(CompanionHeaders.ACCESS_MODE, "INTERNAL")
                        .header(CompanionHeaders.SERVICE_ID, "experience-bff"))
                .andExpect(status().isForbidden());
    }
}
