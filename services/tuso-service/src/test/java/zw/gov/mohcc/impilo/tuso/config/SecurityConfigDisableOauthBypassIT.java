package zw.gov.mohcc.impilo.tuso.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TusoSecurityProbeController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "impilo.security.disable-oauth-for-tests=true"
})
class SecurityConfigDisableOauthBypassIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bypassActivatesOnlyWhenExplicitlyEnabled() throws Exception {
        mockMvc.perform(get("/v1/internal/security-probe")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}
