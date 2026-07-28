package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import zw.gov.mohcc.impilo.pct.integration.DaidzaiEpisodeClient;

/**
 * MCI bulk-mint (W11) — "PCT bulk-mint" from the design doc. Proves idempotent minting
 * (entrySourceRef=casualtyId, the same replay-safe path every entry route already uses), the
 * link-back write, and the fail-safe behaviour when daidzai is unreachable (mints nothing that
 * round, never guesses).
 */
class MciBulkMintServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID INCIDENT = UUID.fromString("00000000-0000-4000-8000-000000000003");

    private EmergencyEpisodeServiceTest.InMemoryRepo episodeRepo;
    private StubDaidzaiEpisodeClient daidzaiClient;
    private MciBulkMintService service;

    @BeforeEach
    void setUp() {
        episodeRepo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        var handoverRepo = new EmergencyEpisodeServiceTest.InMemoryHandoverRepo();
        var outbox = new EmergencyEpisodeServiceTest.CountingOutbox();
        EmergencyEpisodeService episodeService =
                new EmergencyEpisodeService(episodeRepo, handoverRepo, outbox, new ObjectMapper());
        daidzaiClient = new StubDaidzaiEpisodeClient();
        service = new MciBulkMintService(episodeService, daidzaiClient);
    }

    private Map<String, Object> casualty(UUID id, String subjectCpid) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id.toString());
        c.put("subjectCpid", subjectCpid);
        c.put("taggedAt", OffsetDateTime.now().toString());
        return c;
    }

    @Test
    @DisplayName("bulk-mint with no facilityId is refused")
    void missingFacilityIdRefused() {
        assertThatThrownBy(() -> service.bulkMint(TENANT, null, INCIDENT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("each unminted casualty gets its own emergency episode")
    void mintsOneEpisodePerCasualty() {
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        daidzaiClient.casualties = List.of(casualty(c1, "CPID-1"), casualty(c2, null));

        var results = service.bulkMint(TENANT, FACILITY, INCIDENT);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> Boolean.TRUE.equals(r.get("created")));
        assertThat(daidzaiClient.linkCalls).hasSize(2);
    }

    @Test
    @DisplayName("minting is idempotent on the casualty id — a re-run of the same casualty replays, not duplicates")
    void mintIsIdempotentOnCasualtyId() {
        UUID c1 = UUID.randomUUID();
        daidzaiClient.casualties = List.of(casualty(c1, "CPID-1"));

        var first = service.bulkMint(TENANT, FACILITY, INCIDENT);
        var second = service.bulkMint(TENANT, FACILITY, INCIDENT); // daidzai's own "unminted" filter would
                                                                    // normally exclude it, but prove the
                                                                    // mint path ITSELF is also idempotent
        assertThat(first.get(0).get("episodeId")).isEqualTo(second.get(0).get("episodeId"));
        assertThat(first.get(0).get("created")).isEqualTo(true);
        assertThat(second.get(0).get("created")).isEqualTo(false);
    }

    @Test
    @DisplayName("minted episodes carry entry_route=MASS_CASUALTY and start PRE_ARRIVAL (no journey yet)")
    void mintedEpisodeShape() {
        UUID c1 = UUID.randomUUID();
        daidzaiClient.casualties = List.of(casualty(c1, "CPID-1"));

        service.bulkMint(TENANT, FACILITY, INCIDENT);

        var episode = episodeRepo.findByStateIn(List.of("PRE_ARRIVAL")).get(0);
        assertThat(episode.getEntryRoute()).isEqualTo("MASS_CASUALTY");
        assertThat(episode.getSubjectCpid()).isEqualTo("CPID-1");
    }

    @Test
    @DisplayName("when daidzai cannot be reached, the pass mints nothing rather than guessing")
    void unreachableDaidzaiMintsNothing() {
        daidzaiClient.simulateUnreachable = true;
        var results = service.bulkMint(TENANT, FACILITY, INCIDENT);
        assertThat(results).isEmpty();
    }

    static final class StubDaidzaiEpisodeClient extends DaidzaiEpisodeClient {
        List<Map<String, Object>> casualties = List.of();
        final List<Map<String, Object>> linkCalls = new ArrayList<>();
        boolean simulateUnreachable = false;

        StubDaidzaiEpisodeClient() { super(null, "http://unused"); }

        @Override
        public List<Map<String, Object>> listUnmintedMciCasualties(UUID tenantId, UUID incidentId) {
            return simulateUnreachable ? List.of() : casualties;
        }

        @Override
        public void linkMciCasualtyEpisode(UUID tenantId, UUID incidentId, UUID casualtyId, UUID episodeId) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("casualtyId", casualtyId);
            call.put("episodeId", episodeId);
            linkCalls.add(call);
        }
    }
}
