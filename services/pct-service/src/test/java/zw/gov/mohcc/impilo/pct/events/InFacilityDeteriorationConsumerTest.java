package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import zw.gov.mohcc.impilo.pct.core.EmergencyEpisodeService;
import zw.gov.mohcc.impilo.pct.core.EmergencyEpisodeServiceTest;
import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.integration.InpatientActivationLinkClient;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.AdmissionRepository;

/**
 * Consumes inpatient's {@code EMERGENCY_ACTIVATION_RAISED} (W6b) — an in-facility code (ward arrest,
 * post-op deterioration) on an ALREADY-admitted patient, minted onto the admission's existing journey
 * rather than a second one. Resolves the journey via the admission handshake
 * (V018 {@code pct_admissions.inpatient_admission_ref}); an activation with no matching PCT admission
 * is skipped, never guessed at.
 */
class InFacilityDeteriorationConsumerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private EmergencyEpisodeServiceTest.InMemoryRepo episodeRepo;
    private InMemoryAdmissionRepo admissionRepo;
    private StubInpatientActivationLinkClient linkClient;
    private InFacilityDeteriorationConsumer consumer;

    @BeforeEach
    void setUp() {
        episodeRepo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        admissionRepo = new InMemoryAdmissionRepo();
        var handoverRepo = new EmergencyEpisodeServiceTest.InMemoryHandoverRepo();
        var outbox = new EmergencyEpisodeServiceTest.CountingOutbox();
        EmergencyEpisodeService episodeService =
                new EmergencyEpisodeService(episodeRepo, handoverRepo, outbox, new ObjectMapper());
        linkClient = new StubInpatientActivationLinkClient();
        consumer = new InFacilityDeteriorationConsumer(new ObjectMapper(), admissionRepo, episodeService, linkClient);
    }

    private AdmissionEntity seedAdmission(UUID inpatientAdmissionRef, String journeyId) {
        AdmissionEntity a = new AdmissionEntity();
        a.setAdmissionId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setFacilityId(FACILITY);
        a.setJourneyId(journeyId);
        a.setPatientCpid("CPID-W6B-1");
        a.setInpatientAdmissionRef(inpatientAdmissionRef);
        admissionRepo.save(a);
        return a;
    }

    private String outboxMessage(String eventType, Map<String, Object> payload) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("eventType", eventType);
        root.put("payload", payload);
        try {
            return new ObjectMapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("a raised activation with a known admission mints an episode on the admission's EXISTING journey")
    void mintsEpisodeOnExistingJourney() {
        UUID admissionRef = UUID.randomUUID();
        seedAdmission(admissionRef, "J-W6B-1");
        UUID activationId = UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("activationId", activationId.toString());
        payload.put("admissionRef", admissionRef.toString());
        payload.put("origin", "WARD");

        consumer.onInpatientEmergency(outboxMessage("EMERGENCY_ACTIVATION_RAISED", payload));

        List<EmergencyEpisodeEntity> board = episodeRepo.findByStateIn(
                List.of(EmergencyEpisodeState.OPEN_UNTRIAGED.name()));
        assertThat(board).hasSize(1);
        EmergencyEpisodeEntity episode = board.get(0);
        assertThat(episode.getJourneyId()).isEqualTo("J-W6B-1");
        assertThat(episode.getEntryRoute()).isEqualTo("IN_FACILITY_DETERIORATION");
        assertThat(episode.getSubjectCpid()).isEqualTo("CPID-W6B-1");
        assertThat(episode.getFacilityId()).isEqualTo(FACILITY);
    }

    @Test
    @DisplayName("the minted episode id is written back onto the inpatient activation")
    void linksEpisodeBackToActivation() {
        UUID admissionRef = UUID.randomUUID();
        seedAdmission(admissionRef, "J-W6B-2");
        UUID activationId = UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("activationId", activationId.toString());
        payload.put("admissionRef", admissionRef.toString());

        consumer.onInpatientEmergency(outboxMessage("EMERGENCY_ACTIVATION_RAISED", payload));

        assertThat(linkClient.calls).hasSize(1);
        assertThat(linkClient.calls.get(0).get("activationId")).isEqualTo(activationId);
    }

    @Test
    @DisplayName("an activation with no admissionRef is skipped — not every activation follows an admission")
    void noAdmissionRefSkipped() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("activationId", UUID.randomUUID().toString());

        consumer.onInpatientEmergency(outboxMessage("EMERGENCY_ACTIVATION_RAISED", payload));

        assertThat(episodeRepo.findByStateIn(List.of(EmergencyEpisodeState.OPEN_UNTRIAGED.name()))).isEmpty();
        assertThat(linkClient.calls).isEmpty();
    }

    @Test
    @DisplayName("an admissionRef with no matching PCT admission handshake is skipped — never anchored to nothing")
    void unknownAdmissionRefSkipped() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("activationId", UUID.randomUUID().toString());
        payload.put("admissionRef", UUID.randomUUID().toString()); // never seeded

        consumer.onInpatientEmergency(outboxMessage("EMERGENCY_ACTIVATION_RAISED", payload));

        assertThat(episodeRepo.findByStateIn(List.of(EmergencyEpisodeState.OPEN_UNTRIAGED.name()))).isEmpty();
        assertThat(linkClient.calls).isEmpty();
    }

    @Test
    @DisplayName("an unrelated event type on the same topic (e.g. ENDED) is ignored")
    void unrelatedEventTypeIgnored() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("activationId", UUID.randomUUID().toString());
        consumer.onInpatientEmergency(outboxMessage("EMERGENCY_ACTIVATION_ENDED", payload));

        assertThat(episodeRepo.findByStateIn(List.of(EmergencyEpisodeState.OPEN_UNTRIAGED.name()))).isEmpty();
    }

    @Test
    @DisplayName("a redelivered event for the SAME activation mints only once (idempotent on entrySourceRef)")
    void redeliveryIsIdempotent() {
        UUID admissionRef = UUID.randomUUID();
        seedAdmission(admissionRef, "J-W6B-3");
        UUID activationId = UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", TENANT.toString());
        payload.put("activationId", activationId.toString());
        payload.put("admissionRef", admissionRef.toString());
        String message = outboxMessage("EMERGENCY_ACTIVATION_RAISED", payload);

        consumer.onInpatientEmergency(message);
        consumer.onInpatientEmergency(message); // redelivered

        assertThat(episodeRepo.findByStateIn(List.of(EmergencyEpisodeState.OPEN_UNTRIAGED.name()))).hasSize(1);
    }

    /** Minimal in-memory fake — only the methods InFacilityDeteriorationConsumer actually calls. */
    static final class InMemoryAdmissionRepo implements AdmissionRepository {
        final Map<UUID, AdmissionEntity> store = new LinkedHashMap<>();

        @Override public Optional<AdmissionEntity> findByTenantIdAndInpatientAdmissionRef(UUID tenantId, UUID inpatientAdmissionRef) {
            return store.values().stream()
                    .filter(a -> a.getTenantId().equals(tenantId) && inpatientAdmissionRef.equals(a.getInpatientAdmissionRef()))
                    .findFirst();
        }
        @Override public Optional<AdmissionEntity> findByTenantIdAndAdmissionId(UUID tenantId, UUID admissionId) { return Optional.empty(); }
        @Override public List<AdmissionEntity> findByTenantIdAndJourneyId(UUID tenantId, String journeyId) { return List.of(); }
        @Override public List<AdmissionEntity> findByTenantIdAndStatus(UUID tenantId, String status) { return List.of(); }
        @Override public Optional<AdmissionEntity> findByTenantIdAndWardIdAndBedIdAndStatus(UUID tenantId, UUID wardId, UUID bedId, String status) { return Optional.empty(); }
        @Override public long countByTenantIdAndWardIdAndStatus(UUID tenantId, UUID wardId, String status) { return 0; }
        @Override public List<AdmissionEntity> findByFacilityIdAndStatusIn(UUID facilityId, List<String> statuses) { return List.of(); }
        @Override public Optional<AdmissionEntity> findByJourneyIdAndStatusIn(String journeyId, List<String> statuses) { return Optional.empty(); }
        @Override public boolean existsByWardIdAndBedIdAndStatusInAndIdNot(UUID wardId, UUID bedId, List<String> statuses, UUID excludeId) { return false; }

        @Override public <S extends AdmissionEntity> S save(S e) {
            if (e.getAdmissionId() == null) e.setAdmissionId(UUID.randomUUID());
            store.put(e.getAdmissionId(), e);
            return e;
        }
        @Override public <S extends AdmissionEntity> S saveAndFlush(S e) { return save(e); }
        @Override public void flush() { }
        @Override public <S extends AdmissionEntity> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<AdmissionEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public AdmissionEntity getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public AdmissionEntity getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public AdmissionEntity getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends AdmissionEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends AdmissionEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends AdmissionEntity> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<AdmissionEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<AdmissionEntity> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<AdmissionEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(AdmissionEntity e) { store.remove(e.getAdmissionId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends AdmissionEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public List<AdmissionEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<AdmissionEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends AdmissionEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends AdmissionEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends AdmissionEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends AdmissionEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends AdmissionEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }

    /** A subclass overriding the one method under test, avoiding a real network call in a unit test. */
    static final class StubInpatientActivationLinkClient extends InpatientActivationLinkClient {
        final List<Map<String, Object>> calls = new ArrayList<>();

        StubInpatientActivationLinkClient() {
            super(null, "http://unused");
        }

        @Override
        public void linkEpisode(UUID tenantId, UUID activationId, UUID emergencyEpisodeId) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("tenantId", tenantId);
            call.put("activationId", activationId);
            call.put("emergencyEpisodeId", emergencyEpisodeId);
            calls.add(call);
        }
    }
}
