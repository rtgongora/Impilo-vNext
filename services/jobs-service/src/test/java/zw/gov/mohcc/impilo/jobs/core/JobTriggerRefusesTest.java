package zw.gov.mohcc.impilo.jobs.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.jobs.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.jobs.persistence.repository.JobExecutionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Triggering must refuse, and must not leave an execution row behind.
 *
 * <p>Before this, {@code POST /internal/v1/jobs/{id}/trigger} returned {@code 201 Created} with
 * a {@code PENDING} execution. There is no scheduler, no worker, no Kafka producer and no
 * subscriber for {@code JOB_TRIGGERED} anywhere in the estate, so that row could never leave
 * PENDING — 201 promised a run that nothing was ever going to perform.</p>
 *
 * <p>The row-count assertion matters as much as the status code: a refusal that still persisted
 * the execution would leave {@code job_execution} accumulating runs that never ran, which is the
 * same lie told in a table instead of a response.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobTriggerRefusesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT = "00000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JobExecutionRepository executionRepository;

    @Autowired
    private EventOutboxRepository outboxRepository;

    private long givenJobDefinition() throws Exception {
        MvcResult created = mvc.perform(post("/internal/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "test-pod")
                        .header("X-Request-ID", "test-req-job")
                        .header("X-Correlation-ID", "test-corr-job")
                        .header("Idempotency-Key", "idem-job-" + System.nanoTime())
                        .content("{\"tenantId\":\"" + TENANT + "\",\"name\":\"nightly\","
                                + "\"cronExpression\":\"0 0 2 * * *\",\"jobType\":\"REPORT\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = MAPPER.readTree(created.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    @Test
    @DisplayName("Trigger returns 501 and creates NO execution row")
    void triggerRefusesAndPersistsNothing() throws Exception {
        long jobId = givenJobDefinition();
        long executionsBefore = executionRepository.count();
        long outboxBefore = outboxRepository.count();

        mvc.perform(post("/internal/v1/jobs/" + jobId + "/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "test-pod")
                        .header("X-Request-ID", "test-req-job-2")
                        .header("X-Correlation-ID", "test-corr-job-2")
                        .header("Idempotency-Key", "idem-job2-" + System.nanoTime())
                        .content("{}"))
                .andExpect(status().isNotImplemented());

        assertThat(executionRepository.count()).isEqualTo(executionsBefore);
        // No JOB_TRIGGERED event for a job that was not triggered.
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    /**
     * Negative control: 501 must mean "there is no executor", not "this route refuses
     * everything". An unknown definition still 404s, proving the handler resolves the job
     * before refusing.
     */
    @Test
    @DisplayName("An unknown job definition still 404s — the refusal is not a blanket 501")
    void unknownJobStill404s() throws Exception {
        mvc.perform(post("/internal/v1/jobs/999999/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "test-pod")
                        .header("X-Request-ID", "test-req-job-3")
                        .header("X-Correlation-ID", "test-corr-job-3")
                        .header("Idempotency-Key", "idem-job3-" + System.nanoTime())
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
