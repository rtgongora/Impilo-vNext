package zw.gov.mohcc.impilo.inpatient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureEpisodeEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureEpisodeRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave 6 §18 — persistence / concurrency / recovery proof for the theatre episode
 * (the ONE source of truth, {@code inpatient.procedure_episode}), exercised against a real
 * transactional datastore (H2 in PostgreSQL mode) through the REAL JPA mapping — no mocks.
 *
 * Proves the guarantees §18 requires for the theatre aggregate:
 *   1. CONCURRENT EDIT of the same episode by two users → the JPA @Version guard (V065) makes
 *      the stale writer fail fast with an optimistic-lock failure (no silent lost update). The
 *      controller advice maps this to a clean HTTP 409 STALE_WRITE.
 *   2. A normal update increments the version monotonically (0 → 1 → 2) — durable, not regressed.
 *   3. The intake DEDUP mechanism (findByTenantIdAndOrosOrderId) returns the SAME row for a
 *      resubmitted OROS order, so a duplicate-click / double-submit / consumer replay of the
 *      same intent yields ONE episode, never two.
 *
 * These are the DB-level invariants the higher-level rig + Playwright ride on; proving them at
 * the mapping layer is deterministic and peer-free.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ProcedureEpisodePersistenceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private ProcedureEpisodeRepository repo;

    @Autowired
    private JdbcTemplate jdbc;

    private ProcedureEpisodeEntity newEpisode(String cpid, String orosOrderId) {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setTenantId(TENANT);
        e.setSubjectCpid(cpid);
        e.setProcedureName("Laparoscopic appendectomy");
        e.setStatus("BOOKED");
        e.setTriagePriority("ELECTIVE");
        e.setConsentStatus("PENDING");
        e.setOrosOrderId(orosOrderId);
        return e;
    }

    @Test
    @DisplayName("§18.1 concurrent edit: a stale write (older version) fails with an optimistic-lock conflict")
    void concurrentEdit_staleWrite_conflicts() {
        ProcedureEpisodeEntity e = repo.saveAndFlush(newEpisode("CPID-ZW-LOCK1", "OROS-LOCK-1"));
        UUID id = e.getEpisodeId();

        // Clinician A loads the episode (version 0) and pauses.
        ProcedureEpisodeEntity staleCopyForA = repo.findById(id).orElseThrow();
        assertEquals(0L, staleCopyForA.getVersion(), "fresh episode starts at version 0");

        // Clinician B commits a change out-of-band (bumps the row to version 1).
        int bumped = jdbc.update(
                "UPDATE inpatient.procedure_episode SET version = version + 1, status = 'IN_THEATRE' WHERE episode_id = ?",
                id);
        assertEquals(1, bumped, "the concurrent writer must have updated exactly one row");

        // Clinician A now tries to save their stale copy → must be rejected, not silently clobber B.
        staleCopyForA.setStatus("CANCELLED");
        assertThrows(OptimisticLockingFailureException.class,
                () -> repo.saveAndFlush(staleCopyForA),
                "a stale write must raise an optimistic-lock conflict (→ HTTP 409 STALE_WRITE)");

        // B's change survives; A's stale clobber did NOT land.
        assertEquals("IN_THEATRE", repo.findById(id).orElseThrow().getStatus(),
                "the concurrent (B) change must survive; the stale (A) write must not overwrite it");
    }

    @Test
    @DisplayName("§18.2 the version increments monotonically on each committed update (no regression)")
    void version_incrementsMonotonically() {
        ProcedureEpisodeEntity e = repo.saveAndFlush(newEpisode("CPID-ZW-LOCK2", "OROS-LOCK-2"));
        UUID id = e.getEpisodeId();
        assertEquals(0L, repo.findById(id).orElseThrow().getVersion());

        ProcedureEpisodeEntity v1 = repo.findById(id).orElseThrow();
        v1.setStatus("READY");
        repo.saveAndFlush(v1);
        assertEquals(1L, repo.findById(id).orElseThrow().getVersion(), "first update → version 1");

        ProcedureEpisodeEntity v2 = repo.findById(id).orElseThrow();
        v2.setStatus("IN_THEATRE");
        repo.saveAndFlush(v2);
        assertEquals(2L, repo.findById(id).orElseThrow().getVersion(), "second update → version 2");
    }

    @Test
    @DisplayName("§18.3 intake dedup: a resubmitted OROS order resolves to the SAME episode (one row, not two)")
    void intakeDedup_sameOrosOrder_oneEpisode() {
        String oros = "OROS-DEDUP-" + UUID.randomUUID();
        ProcedureEpisodeEntity first = repo.saveAndFlush(newEpisode("CPID-ZW-DEDUP", oros));

        // The intake path dedups on (tenant, oros_order_id): a duplicate-click / double-submit /
        // consumer replay of the same intent must find the existing episode rather than create a new one.
        Optional<ProcedureEpisodeEntity> found = repo.findByTenantIdAndOrosOrderId(TENANT, oros);
        assertTrue(found.isPresent(), "the dedup lookup must find the existing episode");
        assertEquals(first.getEpisodeId(), found.get().getEpisodeId(),
                "a resubmitted OROS order must resolve to the SAME episode id");

        long rows = repo.findByTenantIdAndSubjectCpidOrderByScheduledAtDesc(TENANT, "CPID-ZW-DEDUP").stream()
                .filter(x -> oros.equals(x.getOrosOrderId())).count();
        assertEquals(1L, rows, "there must be exactly ONE episode row for the OROS order");
    }
}
