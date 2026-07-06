package zw.gov.mohcc.impilo.orgregistry.core;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgClaimSubmissionEntity;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Client-side contract test for the START ARC call into workflow-service.
 * Pins the exact request: {@code POST /internal/v1/workflows/instances} carrying
 * the configured adjudication {@code definitionId}, {@code subjectRef}=claim id,
 * and the claim linkage threaded through {@code context}; and that the client
 * reads the returned {@code instanceId}.
 */
class WorkflowServiceClientTest {

    private static final String DEFINITION_ID = "ad1d0000-0000-4000-8000-000000000003";
    private static final String BASE = "http://workflow-test";

    private OrgClaimSubmissionEntity claim(UUID id) {
        OrgClaimSubmissionEntity c = new OrgClaimSubmissionEntity();
        c.setId(id);
        c.setOrganizationId(UUID.randomUUID());
        c.setSubjectHealthId("HID-500");
        c.setClaimedRole("NURSE");
        return c;
    }

    @Test
    void startAdjudication_postsDefinitionAndSubjectRef_andReturnsInstanceId() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WorkflowServiceClient client = new WorkflowServiceClient(builder, true, BASE, "national", DEFINITION_ID);

        UUID tenant = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        server.expect(requestTo(BASE + "/internal/v1/workflows/instances"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.definitionId").value(DEFINITION_ID))
                .andExpect(jsonPath("$.subjectRef").value(claimId.toString()))
                .andExpect(jsonPath("$.context.claimId").value(claimId.toString()))
                .andRespond(withSuccess("{\"instanceId\":\"" + instanceId + "\"}", APPLICATION_JSON));

        Optional<UUID> result = client.startAdjudication(tenant, claim(claimId), "actor-1", "corr-1");

        assertThat(result).contains(instanceId);
        server.verify();
    }

    @Test
    void startAdjudication_disabledReturnsEmptyWithoutCallingWorkflow() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WorkflowServiceClient client = new WorkflowServiceClient(builder, false, BASE, "national", DEFINITION_ID);

        Optional<UUID> result = client.startAdjudication(UUID.randomUUID(), claim(UUID.randomUUID()), "actor-1", "corr-1");

        assertThat(result).isEmpty();
        server.verify(); // no request expected
    }

    @Test
    void startAdjudication_serverErrorReturnsEmpty_honestPending() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WorkflowServiceClient client = new WorkflowServiceClient(builder, true, BASE, "national", DEFINITION_ID);

        server.expect(requestTo(BASE + "/internal/v1/workflows/instances"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        Optional<UUID> result = client.startAdjudication(UUID.randomUUID(), claim(UUID.randomUUID()), "actor-1", "corr-1");

        assertThat(result).isEmpty();
        server.verify();
    }
}
