package zw.gov.mohcc.impilo.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wgv_governance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GovernanceOnboardingWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void singleFacilityOnboardingThenFacilityScopeAllowsLinkedActor() throws Exception {
        UUID tenant = UUID.randomUUID();
        String actor = "health-id-actor-1";
        String body = """
                {
                  "organisationCode": "ORG-%s",
                  "organisationName": "Test Org",
                  "legalName": "Test Org Ltd",
                  "organisationType": "HOSPITAL_GROUP",
                  "facilityId": 4242,
                  "createDefaultUnit": true
                }
                """.formatted(tenant.toString().substring(0, 8));

        mockMvc.perform(post("/v1/internal/governance/onboarding/single-facility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Actor-ID", actor)
                        .header("X-Actor-Type", "OPERATOR")
                        .header("X-Purpose-Of-Use", "OPERATIONS")
                        .header("X-Device-Fingerprint", "test")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        String assignBody = """
                {
                  "subjectType": "USER",
                  "subjectId": "%s",
                  "roleDefinitionId": "%s",
                  "targetType": "FACILITY",
                  "targetId": "4242",
                  "initialStatus": "ACTIVE",
                  "primary": true,
                  "secondary": false
                }
                """;

        String roles = mockMvc.perform(post("/v1/internal/governance/role-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Actor-ID", actor)
                        .header("X-Actor-Type", "OPERATOR")
                        .header("X-Purpose-Of-Use", "OPERATIONS")
                        .header("X-Device-Fingerprint", "test")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .content("""
                                {
                                  "roleCode": "CUSTOM_FACILITY",
                                  "name": "Custom",
                                  "description": "d",
                                  "roleCategory": "ADMINISTRATIVE",
                                  "roleLevel": "FACILITY_LEVEL",
                                  "allowedTargetTypes": ["FACILITY"],
                                  "requiresProvider": false,
                                  "requiresProfessionalStanding": false,
                                  "specialGovernance": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String roleId = objectMapper.readTree(roles).path("data").path("id").asText();

        mockMvc.perform(post("/v1/internal/governance/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Actor-ID", actor)
                        .header("X-Actor-Type", "OPERATOR")
                        .header("X-Purpose-Of-Use", "OPERATIONS")
                        .header("X-Device-Fingerprint", "test")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .content(String.format(assignBody, actor, roleId)))
                .andExpect(status().isCreated());

        String eval = """
                {
                  "actorHealthId": "%s",
                  "providerPublicId": null,
                  "tusoFacilityId": 4242
                }
                """.formatted(actor);

        mockMvc.perform(post("/v1/internal/governance/scope/evaluate-facility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Actor-ID", actor)
                        .header("X-Actor-Type", "OPERATOR")
                        .header("X-Purpose-Of-Use", "OPERATIONS")
                        .header("X-Device-Fingerprint", "test")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .content(eval))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true));
    }

    @Test
    void multiFacilityOnboardingLinksAllFacilities() throws Exception {
        UUID tenant = UUID.randomUUID();
        String actor = "health-id-actor-multi";
        String body = """
                {
                  "organisationCode": "MULTI-%s",
                  "organisationName": "Multi Org",
                  "legalName": "Multi Org Ltd",
                  "organisationType": "NETWORK",
                  "facilityIds": [1001, 1002, 1003],
                  "createDefaultUnit": true
                }
                """.formatted(tenant.toString().substring(0, 8));

        mockMvc.perform(post("/v1/internal/governance/onboarding/multi-facility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", tenant.toString())
                        .header("X-Actor-ID", actor)
                        .header("X-Actor-Type", "OPERATOR")
                        .header("X-Purpose-Of-Use", "OPERATIONS")
                        .header("X-Device-Fingerprint", "test")
                        .header("X-Correlation-ID", UUID.randomUUID().toString())
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.linkedFacilityIds.length()").value(3));
    }
}
