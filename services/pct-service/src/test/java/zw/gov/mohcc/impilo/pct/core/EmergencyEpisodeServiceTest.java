package zw.gov.mohcc.impilo.pct.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyHandoverRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmergencyEpisodeServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private InMemoryRepo repo;
    private InMemoryHandoverRepo handoverRepo;
    private CountingOutbox outbox;
    private EmergencyEpisodeService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryRepo();
        handoverRepo = new InMemoryHandoverRepo();
        outbox = new CountingOutbox();
        service = new EmergencyEpisodeService(repo, handoverRepo, outbox, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    /** An episode in OPEN_IN_CARE, ready to have a handover requested against it. */
    private EmergencyEpisodeEntity episodeInCare() {
        EmergencyEpisodeEntity e = new EmergencyEpisodeEntity();
        e.setEpisodeId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setFacilityId(FACILITY);
        e.setEpisodeReference("EM-TEST");
        e.setEntryRoute("WALK_IN");
        e.setArrivedAt(OffsetDateTime.now());
        e.setState(EmergencyEpisodeState.OPEN_IN_CARE.name());
        return repo.save(e);
    }

    /**
     * The care-first property, asserted structurally rather than by inspection: opening an episode
     * touches nothing but this service's own repository. A patient who arrives while daidzai, VITO,
     * COSTA or TUSO is unavailable still gets an episode.
     */
    @Test
    @DisplayName("an episode opens with no peer service reachable")
    void openingAnEpisodeDependsOnNoPeer() {
        var result = service.open(walkIn().build());

        assertTrue(result.created());
        assertNotNull(result.episode().getEpisodeId());
        assertEquals(1, repo.store.size());
        // Care-first asserted structurally: every constructor parameter is one of PCT's own tables or
        // a serialiser. A *ServiceClient here would be a peer able to block an emergency registration.
        for (var p : EmergencyEpisodeService.class.getDeclaredConstructors()[0].getParameterTypes()) {
            assertFalse(p.getSimpleName().endsWith("ServiceClient") || p.getSimpleName().endsWith("Client"),
                    "EmergencyEpisodeService must not depend on " + p.getSimpleName()
                            + " — a peer client here can block an emergency registration");
        }
    }

    @Test
    @DisplayName("opening emits a routed lifecycle event, in the same transaction")
    void openingEmitsALifecycleEvent() {
        service.open(walkIn().journeyId("J1").build());

        assertEquals(1, outbox.events.size());
        var ev = outbox.events.get(0);
        assertEquals("EMERGENCY_EPISODE_OPENED", ev.getEventType());
        assertEquals("EMERGENCY_EPISODE", ev.getAggregateType());
        // That this type actually ROUTES to a topic a consumer subscribes to is asserted in
        // ClinicalEventTopicInventoryTest, which owns that layer. Split deliberately: this test
        // proves the emit happened, that one proves it is addressable. An emit with no route is the
        // defect that hid pct.ed.critical_result for the life of the ED diagnostics lane.
    }

    @Test
    @DisplayName("the event payload carries correlation, not a copy of the clinical record")
    void eventPayloadCarriesNoClinicalContent() {
        service.open(walkIn().journeyId("J1").build());
        String payload = outbox.events.get(0).getPayload();

        assertTrue(payload.contains("episodeId"));
        assertTrue(payload.contains("journeyId"));
        // A copy of the acuity or the disposition here would be a second, staler answer to a
        // question another table already owns.
        assertFalse(payload.contains("acuity"), payload);
        assertFalse(payload.contains("disposition"), payload);
    }

    @Test
    @DisplayName("a replay emits nothing, because nothing happened")
    void aReplayEmitsNoEvent() {
        service.open(ambulance("ems-9").build());
        int after = outbox.events.size();
        service.open(ambulance("ems-9").build());
        assertEquals(after, outbox.events.size(),
                "a replayed pre-alert is not a new episode and must not look like one to consumers");
    }

    @Test
    @DisplayName("an unidentified patient is a first-class state, not a validation failure")
    void anUnidentifiedPatientCanBeRegistered() {
        var result = service.open(walkIn().subjectCpid(null).build());

        assertEquals("UNKNOWN", result.episode().getIdentityMode());
        assertNull(result.episode().getSubjectCpid());
    }

    @Test
    @DisplayName("a care-first case with a provisional record is PROVISIONAL, not UNKNOWN")
    void aProvisionalIdentityIsDistinguishedFromNoIdentity() {
        var result = service.open(walkIn().subjectCpid(null).emergencyCaseId(UUID.randomUUID()).build());
        assertEquals("PROVISIONAL", result.episode().getIdentityMode());
    }

    /** Replay is normal here. Re-pushing one ambulance pre-alert must not mint a second episode. */
    @Test
    @DisplayName("a replayed pre-alert returns the existing episode rather than a second one")
    void mintIsIdempotentOnEntrySourceRef() {
        var first = service.open(ambulance("ems-mission-42").build());
        var second = service.open(ambulance("ems-mission-42").build());

        assertTrue(first.created());
        assertFalse(second.created(), "the replay must be reported as not-created");
        assertEquals(first.episode().getEpisodeId(), second.episode().getEpisodeId());
        assertEquals(1, repo.store.size());
    }

    /** Two people can walk in. A walk-in has no source ref, so two episodes is the right answer. */
    @Test
    @DisplayName("two walk-ins with no source reference produce two episodes")
    void walkInsAreNotDeduplicated() {
        service.open(walkIn().build());
        service.open(walkIn().build());
        assertEquals(2, repo.store.size());
    }

    @Test
    @DisplayName("a pre-alert opens unanchored, and arrival is what anchors it")
    void preArrivalIsTheOnlyUnanchoredState() {
        var opened = service.open(ambulance("ems-1").journeyId(null).build());
        assertEquals(EmergencyEpisodeState.PRE_ARRIVAL.name(), opened.episode().getState());
        assertNull(opened.episode().getJourneyId());
        assertNull(opened.episode().getAnchorResolvedAt());

        var anchored = service.resolveAnchorOnArrival(
                opened.episode().getEpisodeId(), TENANT, "J-ABC", 99L);

        assertEquals(EmergencyEpisodeState.OPEN_UNTRIAGED.name(), anchored.getState());
        assertEquals("J-ABC", anchored.getJourneyId());
        assertNotNull(anchored.getAnchorResolvedAt());
    }

    /** Re-arrival is a replay, not a second journey. */
    @Test
    @DisplayName("re-anchoring an already-anchored episode does not fork a second journey")
    void reAnchoringIsIdempotent() {
        var opened = service.open(walkIn().journeyId("J-FIRST").build());
        var again = service.resolveAnchorOnArrival(
                opened.episode().getEpisodeId(), TENANT, "J-SECOND", 1L);
        assertEquals("J-FIRST", again.getJourneyId(),
                "a second journey for one facility visit would double-count the patient");
    }

    /**
     * The transition the pack exists to forbid. Requesting is not handing over.
     */
    @Test
    @DisplayName("an in-care episode cannot be closed as handed over")
    void aRequestCannotCloseAnEpisode() {
        var e = service.open(walkIn().journeyId("J1").build()).episode();
        service.transition(e.getEpisodeId(), TENANT, EmergencyEpisodeState.OPEN_IN_CARE, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.transition(e.getEpisodeId(), TENANT,
                        EmergencyEpisodeState.CLOSED_HANDED_OVER, "ADMITTED"));
        assertTrue(ex.getMessage().contains("cannot move from OPEN_IN_CARE"));
    }

    @Test
    @DisplayName("a declined handover returns the patient to emergency care")
    void aDeclinedHandoverReturnsThePatient() {
        var e = service.open(walkIn().journeyId("J1").build()).episode();
        service.transition(e.getEpisodeId(), TENANT, EmergencyEpisodeState.OPEN_IN_CARE, null);
        service.transition(e.getEpisodeId(), TENANT, EmergencyEpisodeState.OPEN_AWAITING_ACCEPTANCE, null);

        var back = service.transition(e.getEpisodeId(), TENANT, EmergencyEpisodeState.OPEN_IN_CARE, null);

        assertEquals(EmergencyEpisodeState.OPEN_IN_CARE.name(), back.getState());
        assertNull(back.getClosedAt(), "the episode is open again, not closed");
    }

    @Test
    @DisplayName("closing without an outcome is refused")
    void closureRequiresAnOutcome() {
        var e = service.open(walkIn().journeyId("J1").build()).episode();
        service.transition(e.getEpisodeId(), TENANT, EmergencyEpisodeState.OPEN_IN_CARE, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.transition(e.getEpisodeId(), TENANT,
                        EmergencyEpisodeState.CLOSED_DISCHARGED, null));
        assertTrue(ex.getMessage().contains("counted"),
                "the message must say why: an outcome-less closure is counted as a completed one");
    }

    @Test
    @DisplayName("an episode from another tenant is not visible")
    void tenantIsolation() {
        var e = service.open(walkIn().journeyId("J1").build()).episode();
        UUID otherTenant = UUID.fromString("00000000-0000-4000-8000-0000000000ff");
        assertThrows(IllegalArgumentException.class,
                () -> service.get(e.getEpisodeId(), otherTenant));
    }

    @Test
    @DisplayName("confirming a location resets the staleness clock")
    void confirmingLocationResetsTheClock() {
        var e = service.open(walkIn().journeyId("J1").build()).episode();
        OffsetDateTime before = e.getLocationConfirmedAt();
        assertNotNull(before, "the clock must start from a real confirmation, not from null");

        var moved = service.confirmLocation(e.getEpisodeId(), TENANT, "CT_SCANNER");

        assertEquals("CT_SCANNER", moved.getCurrentLocation());
        assertTrue(!moved.getLocationConfirmedAt().isBefore(before));
    }

    @Test
    @DisplayName("the board returns open episodes and excludes closed ones")
    void boardShowsOnlyOpenEpisodes() {
        var open = service.open(walkIn().journeyId("J1").build()).episode();
        var closing = service.open(walkIn().journeyId("J2").build()).episode();
        service.transition(closing.getEpisodeId(), TENANT, EmergencyEpisodeState.OPEN_IN_CARE, null);
        service.transition(closing.getEpisodeId(), TENANT,
                EmergencyEpisodeState.CLOSED_DISCHARGED, "DISCHARGED_HOME");

        List<EmergencyEpisodeEntity> board = service.board(TENANT, FACILITY);

        assertEquals(1, board.size());
        assertEquals(open.getEpisodeId(), board.get(0).getEpisodeId());
    }

    @Test
    @DisplayName("the episode reference avoids characters that sound alike when read aloud")
    void referenceIsSafeToReadAloud() {
        var ref = service.open(walkIn().build()).episode().getEpisodeReference();
        assertTrue(ref.startsWith("EM-"), ref);
        String suffix = ref.substring(ref.indexOf('-', 3) + 1);
        for (char c : suffix.toCharArray()) {
            assertFalse("BISOZ".indexOf(c) >= 0,
                    "reference " + ref + " contains a character that is misheard across a resus room");
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private Cmd walkIn() {
        return new Cmd().entryRoute("WALK_IN").subjectCpid("cpid-1");
    }

    private Cmd ambulance(String missionRef) {
        return new Cmd().entryRoute("AMBULANCE").entrySourceService("daidzai-service")
                .entrySourceRef(missionRef).subjectCpid(null);
    }

    /** A tiny builder so each test states only what it varies. */
    private static final class Cmd {
        private String entryRoute;
        private String entrySourceService;
        private String entrySourceRef;
        private String subjectCpid;
        private String journeyId;
        private UUID emergencyCaseId;

        Cmd entryRoute(String v) { this.entryRoute = v; return this; }
        Cmd entrySourceService(String v) { this.entrySourceService = v; return this; }
        Cmd entrySourceRef(String v) { this.entrySourceRef = v; return this; }
        Cmd subjectCpid(String v) { this.subjectCpid = v; return this; }
        Cmd journeyId(String v) { this.journeyId = v; return this; }
        Cmd emergencyCaseId(UUID v) { this.emergencyCaseId = v; return this; }

        EmergencyEpisodeService.OpenEpisodeCommand build() {
            return new EmergencyEpisodeService.OpenEpisodeCommand(
                    TENANT, FACILITY, entryRoute, entrySourceService, entrySourceRef, null,
                    null, subjectCpid, journeyId, null, null, emergencyCaseId, false, null, "actor-1");
        }
    }

    /** Captures what the service told the estate. */
    public static final class CountingOutbox implements zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository {
        private final List<zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> events = new ArrayList<>();

        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> S save(S e) {
            events.add(e); return e;
        }
        @Override public List<zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc() { return List.of(); }
        @Override public void flush() { }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity getOne(Long id) { throw new UnsupportedOperationException(); }
        @Override public zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity getById(Long id) { throw new UnsupportedOperationException(); }
        @Override public zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity getReferenceById(Long id) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> findAll() { return new ArrayList<>(events); }
        @Override public List<zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> findAllById(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> findById(Long id) { return Optional.empty(); }
        @Override public boolean existsById(Long id) { return false; }
        @Override public long count() { return events.size(); }
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public void delete(zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { throw new UnsupportedOperationException(); }
        @Override public List<zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }

    /**
     * Hand-written in-memory repository rather than a mock. A mock would let the test pass while the
     * query methods were wrong; this at least honours the tenant filter and the state filter, which
     * are the two things the board and the tenant-isolation tests are actually about.
     */
    public static final class InMemoryRepo implements EmergencyEpisodeRepository {
        private final Map<UUID, EmergencyEpisodeEntity> store = new LinkedHashMap<>();

        @Override public Optional<EmergencyEpisodeEntity> findByEpisodeIdAndTenantId(UUID id, UUID tenant) {
            return Optional.ofNullable(store.get(id)).filter(e -> e.getTenantId().equals(tenant));
        }

        @Override public Optional<EmergencyEpisodeEntity> findByTenantIdAndEntryRouteAndEntrySourceRef(
                UUID tenant, String route, String ref) {
            return store.values().stream()
                    .filter(e -> e.getTenantId().equals(tenant))
                    .filter(e -> route.equals(e.getEntryRoute()))
                    .filter(e -> ref.equals(e.getEntrySourceRef()))
                    .findFirst();
        }

        @Override public List<EmergencyEpisodeEntity>
        findByTenantIdAndFacilityIdAndStateInOrderByArrivedAtDesc(UUID tenant, UUID facility, List<String> states) {
            List<EmergencyEpisodeEntity> out = new ArrayList<>();
            for (EmergencyEpisodeEntity e : store.values()) {
                if (e.getTenantId().equals(tenant) && e.getFacilityId().equals(facility)
                        && states.contains(e.getState())) {
                    out.add(e);
                }
            }
            out.sort((a, b) -> b.getArrivedAt().compareTo(a.getArrivedAt()));
            return out;
        }

        /** Cross-tenant open-episode scan — the alert sweep's input (W5). */
        @Override public List<EmergencyEpisodeEntity> findByStateIn(List<String> states) {
            List<EmergencyEpisodeEntity> out = new ArrayList<>();
            for (EmergencyEpisodeEntity e : store.values()) {
                if (states.contains(e.getState())) {
                    out.add(e);
                }
            }
            return out;
        }

        @Override public int repointSubjectCpid(UUID tenant, String provisional, String confirmed,
                                                java.time.OffsetDateTime now) {
            int n = 0;
            for (EmergencyEpisodeEntity e : store.values()) {
                if (e.getTenantId().equals(tenant) && provisional.equals(e.getSubjectCpid())) {
                    e.setSubjectCpid(confirmed);
                    e.setIdentityMode("KNOWN");
                    // The real query now binds these; the fake must too, or it would keep passing
                    // while the resolution timestamp silently stopped being written.
                    e.setIdentityResolvedAt(now);
                    e.setUpdatedAt(now);
                    n++;
                }
            }
            return n;
        }

        @Override public <S extends EmergencyEpisodeEntity> S save(S entity) {
            store.put(entity.getEpisodeId(), entity);
            return entity;
        }

        // ── unused JpaRepository surface ──────────────────────────────────────────────────────
        @Override public void flush() { }
        @Override public <S extends EmergencyEpisodeEntity> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends EmergencyEpisodeEntity> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<EmergencyEpisodeEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public EmergencyEpisodeEntity getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyEpisodeEntity getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyEpisodeEntity getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyEpisodeEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyEpisodeEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyEpisodeEntity> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<EmergencyEpisodeEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<EmergencyEpisodeEntity> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<EmergencyEpisodeEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public void delete(EmergencyEpisodeEntity e) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends EmergencyEpisodeEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { throw new UnsupportedOperationException(); }
        @Override public List<EmergencyEpisodeEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<EmergencyEpisodeEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyEpisodeEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyEpisodeEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyEpisodeEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyEpisodeEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyEpisodeEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // The acceptance handshake (W9). V200's chk_ee_handover_required has been unsatisfiable since
    // the day it was written — nothing set handover_id. These tests prove requestHandover /
    // acceptHandover / declineHandover / expireHandover actually close that gap end to end.
    // ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Requesting a handover moves the episode to OPEN_AWAITING_ACCEPTANCE without touching handover_id")
    void requestHandoverMovesEpisodeWithoutClosingIt() {
        EmergencyEpisodeEntity e = episodeInCare();
        var h = service.requestHandover(e.getEpisodeId(), TENANT, "ADMISSION", "inpatient-service", null,
                "nurse-A", "needs a bed");

        assertEquals("PENDING", h.getStatus());
        EmergencyEpisodeEntity reloaded = repo.findByEpisodeIdAndTenantId(e.getEpisodeId(), TENANT).orElseThrow();
        assertEquals(EmergencyEpisodeState.OPEN_AWAITING_ACCEPTANCE.name(), reloaded.getState());
        assertNull(reloaded.getHandoverId(), "a request is not a handover — handover_id must stay unset");
    }

    @Test
    @DisplayName("A second pending request for the same episode is refused")
    void secondPendingRequestRefused() {
        EmergencyEpisodeEntity e = episodeInCare();
        service.requestHandover(e.getEpisodeId(), TENANT, "ADMISSION", "inpatient-service", null, "nurse-A", "r1");
        assertThrows(IllegalStateException.class, () ->
                service.requestHandover(e.getEpisodeId(), TENANT, "THEATRE", "theatre", null, "doctor-B", "r2"));
    }

    @Test
    @DisplayName("Requesting a handover from a state that cannot reach OPEN_AWAITING_ACCEPTANCE is refused by the FSM")
    void requestHandoverFromWrongStateRefused() {
        EmergencyEpisodeEntity e = episodeInCare();
        e.setState(EmergencyEpisodeState.OPEN_UNTRIAGED.name());
        repo.save(e);
        assertThrows(IllegalStateException.class, () ->
                service.requestHandover(e.getEpisodeId(), TENANT, "ADMISSION", "inpatient-service", null, "nurse-A", "r"));
    }

    @Test
    @DisplayName("Accepting requires the accepting party's own record id, and rejects a blank one")
    void acceptRequiresAcceptingRef() {
        EmergencyEpisodeEntity e = episodeInCare();
        var h = service.requestHandover(e.getEpisodeId(), TENANT, "ADMISSION", "inpatient-service", null, "nurse-A", "r");
        assertThrows(IllegalArgumentException.class, () ->
                service.acceptHandover(h.getHandoverId(), TENANT, "clerk", "  ", "inpatient-service"));
        assertThrows(IllegalArgumentException.class, () ->
                service.acceptHandover(h.getHandoverId(), TENANT, "clerk", null, "inpatient-service"));
    }

    @Test
    @DisplayName("Accepting closes the episode CLOSED_HANDED_OVER and stamps handover_id — the invariant satisfied end to end")
    void acceptClosesEpisodeWithHandoverId() {
        EmergencyEpisodeEntity e = episodeInCare();
        var h = service.requestHandover(e.getEpisodeId(), TENANT, "ADMISSION", "inpatient-service", null, "nurse-A", "r");

        EmergencyEpisodeEntity closed = service.acceptHandover(h.getHandoverId(), TENANT, "clerk", "ADM-4471", "inpatient-service");

        assertEquals(EmergencyEpisodeState.CLOSED_HANDED_OVER.name(), closed.getState());
        assertEquals(h.getHandoverId(), closed.getHandoverId());
        assertEquals("ADMITTED", closed.getOutcome());
        assertNotNull(closed.getClosedAt());
        assertEquals("ACCEPTED", handoverRepo.findByHandoverIdAndTenantId(h.getHandoverId(), TENANT).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("A handover that is already resolved cannot be accepted again")
    void cannotAcceptAlreadyResolvedHandover() {
        EmergencyEpisodeEntity e = episodeInCare();
        var h = service.requestHandover(e.getEpisodeId(), TENANT, "ADMISSION", "inpatient-service", null, "nurse-A", "r");
        service.acceptHandover(h.getHandoverId(), TENANT, "clerk", "ADM-1", "inpatient-service");
        assertThrows(IllegalArgumentException.class, () ->
                service.acceptHandover(h.getHandoverId(), TENANT, "clerk2", "ADM-2", "inpatient-service"));
    }

    @Test
    @DisplayName("Declining returns the episode to OPEN_IN_CARE, never to a closed state, and requires a reason")
    void declineReturnsToOpenInCareAndRequiresReason() {
        EmergencyEpisodeEntity e = episodeInCare();
        var h = service.requestHandover(e.getEpisodeId(), TENANT, "THEATRE", "theatre", null, "doctor-B", "r");

        assertThrows(IllegalArgumentException.class, () ->
                service.declineHandover(h.getHandoverId(), TENANT, "surgeon-C", ""));

        EmergencyEpisodeEntity back = service.declineHandover(h.getHandoverId(), TENANT, "surgeon-C", "no slot");
        assertEquals(EmergencyEpisodeState.OPEN_IN_CARE.name(), back.getState());
        assertNull(back.getClosedAt(), "a decline must never close the episode");
        assertEquals("DECLINED", handoverRepo.findByHandoverIdAndTenantId(h.getHandoverId(), TENANT).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("After a decline, a new handover request is allowed — the slot is freed, not abandoned")
    void declineFreesTheSlotForANewRequest() {
        EmergencyEpisodeEntity e = episodeInCare();
        var h1 = service.requestHandover(e.getEpisodeId(), TENANT, "THEATRE", "theatre", null, "doctor-B", "r1");
        service.declineHandover(h1.getHandoverId(), TENANT, "surgeon-C", "no slot");

        var h2 = service.requestHandover(e.getEpisodeId(), TENANT, "ADMISSION", "inpatient-service", null, "nurse-A", "r2");
        assertEquals("PENDING", h2.getStatus());
    }

    @Test
    @DisplayName("Expiring returns the episode to OPEN_IN_CARE and can link a rito case — no timeout ever closes the episode itself")
    void expireReturnsToOpenInCareAndLinksRito() {
        EmergencyEpisodeEntity e = episodeInCare();
        var h = service.requestHandover(e.getEpisodeId(), TENANT, "ADMISSION", "inpatient-service", null, "nurse-A", "r");

        EmergencyEpisodeEntity back = service.expireHandover(h.getHandoverId(), TENANT, "system:sla-monitor", "RITO-9001");
        assertEquals(EmergencyEpisodeState.OPEN_IN_CARE.name(), back.getState());
        var reloadedHandover = handoverRepo.findByHandoverIdAndTenantId(h.getHandoverId(), TENANT).orElseThrow();
        assertEquals("EXPIRED", reloadedHandover.getStatus());
        assertEquals("RITO-9001", reloadedHandover.getRitoCaseRef());
    }

    @Test
    @DisplayName("Every handover state transition emits an outbox event that routes, not the catch-all")
    void handoverTransitionsEmitOutboxEvents() {
        EmergencyEpisodeEntity e = episodeInCare();
        var h = service.requestHandover(e.getEpisodeId(), TENANT, "ADMISSION", "inpatient-service", null, "nurse-A", "r");
        service.acceptHandover(h.getHandoverId(), TENANT, "clerk", "ADM-1", "inpatient-service");

        List<String> types = outbox.findAll().stream()
                .map(zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity::getEventType).toList();
        assertTrue(types.contains("EMERGENCY_HANDOVER_REQUESTED"));
        assertTrue(types.contains("EMERGENCY_HANDOVER_ACCEPTED"));
        assertTrue(types.contains("EMERGENCY_EPISODE_STATE_CHANGED"));
    }

    /** Hand-written in-memory handover repository, same rationale as InMemoryRepo above. */
    public static final class InMemoryHandoverRepo implements EmergencyHandoverRepository {
        private final Map<UUID, zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> store = new java.util.LinkedHashMap<>();

        @Override public Optional<zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity>
        findByHandoverIdAndTenantId(UUID handoverId, UUID tenantId) {
            var h = store.get(handoverId);
            return (h != null && h.getTenantId().equals(tenantId)) ? Optional.of(h) : Optional.empty();
        }

        @Override public Optional<zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity>
        findPending(UUID tenantId, UUID episodeId) {
            return store.values().stream()
                    .filter(h -> h.getTenantId().equals(tenantId) && h.getEpisodeId().equals(episodeId)
                            && "PENDING".equals(h.getStatus()))
                    .findFirst();
        }

        @Override public List<zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity>
        findByTenantIdAndEpisodeIdOrderByRequestedAtDesc(UUID tenantId, UUID episodeId) {
            return store.values().stream()
                    .filter(h -> h.getTenantId().equals(tenantId) && h.getEpisodeId().equals(episodeId))
                    .sorted((a, b) -> b.getRequestedAt().compareTo(a.getRequestedAt())).toList();
        }

        @Override public List<zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity>
        findPendingByTarget(UUID tenantId, String targetType) {
            return store.values().stream()
                    .filter(h -> h.getTenantId().equals(tenantId) && targetType.equals(h.getTargetType())
                            && "PENDING".equals(h.getStatus()))
                    .sorted((a, b) -> a.getRequestedAt().compareTo(b.getRequestedAt())).toList();
        }

        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> S save(S h) {
            if (h.getHandoverId() == null) h.setHandoverId(UUID.randomUUID());
            store.put(h.getHandoverId(), h);
            return h;
        }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> S saveAndFlush(S h) { return save(h); }
        @Override public void flush() { }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> List<S> saveAllAndFlush(Iterable<S> hs) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> hs) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> List<S> saveAll(Iterable<S> hs) { throw new UnsupportedOperationException(); }
        @Override public List<zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity h) { store.remove(h.getHandoverId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> hs) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public List<zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }
}
