package zw.gov.mohcc.impilo.clinical.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ClinicalKnowledgePathwayApiIT {

    private static final String DEMO_PATHWAY = "f0000001-0001-4001-8001-000000000001";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listPathways_returnsDataArray() throws Exception {
        mockMvc.perform(get("/internal/v1/clinical/pathways"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void startPathwaySession_includesFirstStepPrompt() throws Exception {
        mockMvc.perform(
                        post("/internal/v1/clinical/pathways/sessions")
                                .header("x-tenant-id", "moh-zw-it")
                                .header("x-actor-id", "actor-it")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pathway_id\":\"" + DEMO_PATHWAY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session_id").exists())
                .andExpect(jsonPath("$.data.current_prompt").exists())
                .andExpect(jsonPath("$.data.data_capture_schema").exists());
    }
}
