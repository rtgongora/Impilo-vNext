package zw.gov.mohcc.impilo.offlinesync.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import zw.gov.mohcc.impilo.offlinesync.domain.SyncStatus;
import zw.gov.mohcc.impilo.offlinesync.persistence.entity.SyncPackEntity;
import zw.gov.mohcc.impilo.offlinesync.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.offlinesync.persistence.repository.SyncPackRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replay must refuse, and must leave the pack alone.
 *
 * <p>Before this, {@code POST /internal/v1/sync-packs/{id}/replay} returned {@code 200} with
 * {@code status=SYNCED} and a {@code syncedAt} stamp, under the comment
 * {@code // Simulate replay: mark as SYNCED}. Nothing applied the pack's payload to any system
 * of record then, and nothing does now — so the only honest answer is a refusal.</p>
 *
 * <p>The status assertion is the load-bearing one: an edge device that believes its queue
 * synced is entitled to discard the local copy, so a false success here loses clinical data
 * rather than merely misreporting.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncPackReplayRefusesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String FACILITY = "00000000-0000-4000-8000-0000000000f1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SyncPackRepository syncPackRepository;

    @Autowired
    private EventOutboxRepository outboxRepository;

    private SyncPackEntity givenPendingPack() throws Exception {
        MvcResult created = mvc.perform(post("/internal/v1/sync-packs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "test-pod")
                        .header("X-Request-ID", "test-req-replay")
                        .header("X-Correlation-ID", "test-corr-replay")
                        .header("Idempotency-Key", "idem-replay-" + System.nanoTime())
                        .content("{\"tenantId\":\"" + TENANT + "\",\"facilityId\":\"" + FACILITY
                                + "\",\"deviceId\":\"device-1\",\"payload\":\"{}\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = MAPPER.readTree(created.getResponse().getContentAsString());
        return syncPackRepository.findById(body.get("id").asLong()).orElseThrow();
    }

    @Test
    @DisplayName("Replay returns 501 and does NOT mark the pack SYNCED")
    void replayRefusesAndLeavesPackUnsynced() throws Exception {
        SyncPackEntity pack = givenPendingPack();
        long outboxBefore = outboxRepository.count();

        mvc.perform(post("/internal/v1/sync-packs/" + pack.getId() + "/replay")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "test-pod")
                        .header("X-Request-ID", "test-req-replay-2")
                        .header("X-Correlation-ID", "test-corr-replay-2")
                        .header("Idempotency-Key", "idem-replay2-" + System.nanoTime()))
                .andExpect(status().isNotImplemented());

        SyncPackEntity after = syncPackRepository.findById(pack.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SyncStatus.PENDING.name());
        assertThat(after.getStatus()).isNotEqualTo(SyncStatus.SYNCED.name());
        assertThat(after.getSyncedAt()).isNull();

        // No SYNC_PACK_REPLAYED event may be emitted for a replay that did not happen.
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    /**
     * Negative control on the refusal itself: 501 must mean "replay is not built", not
     * "this endpoint refuses everything". An unknown id still resolves to 404, which proves
     * the handler runs and looks the pack up before refusing.
     */
    @Test
    @DisplayName("An unknown sync pack still 404s — the refusal is not a blanket 501")
    void unknownPackStill404s() throws Exception {
        mvc.perform(post("/internal/v1/sync-packs/999999/replay")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Pod-ID", "test-pod")
                        .header("X-Request-ID", "test-req-replay-3")
                        .header("X-Correlation-ID", "test-corr-replay-3")
                        .header("Idempotency-Key", "idem-replay3-" + System.nanoTime()))
                .andExpect(status().isNotFound());
    }
}
