package zw.gov.mohcc.impilo.pct.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyOrderSetInstanceEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyOrderSetItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyOrderSetInstanceRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyOrderSetItemRepository;

/**
 * Order-set invocation/order/decline (pct V206, W7b), exercised without a database. Reuses
 * {@link EmergencyEpisodeServiceTest.InMemoryRepo} for the episode-existence guard rather than
 * duplicating it.
 */
class EmergencyOrderSetServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private EmergencyEpisodeServiceTest.InMemoryRepo episodeRepo;
    private InMemoryInstanceRepo instanceRepo;
    private InMemoryItemRepo itemRepo;
    private EmergencyOrderSetService service;
    private UUID episodeId;

    @BeforeEach
    void setUp() {
        episodeRepo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        instanceRepo = new InMemoryInstanceRepo();
        itemRepo = new InMemoryItemRepo();
        service = new EmergencyOrderSetService(episodeRepo, instanceRepo, itemRepo);

        EmergencyEpisodeEntity episode = new EmergencyEpisodeEntity();
        episodeId = UUID.randomUUID();
        episode.setEpisodeId(episodeId);
        episode.setTenantId(TENANT);
        episode.setFacilityId(FACILITY);
        episode.setEpisodeReference("EM-OS-TEST");
        episode.setEntryRoute("WALK_IN");
        episode.setArrivedAt(OffsetDateTime.now());
        episode.setState(EmergencyEpisodeState.OPEN_IN_CARE.name());
        episodeRepo.save(episode);
    }

    private Map<String, Object> sepsisBundleBody() {
        Map<String, Object> item1 = Map.of("orderCode", "BLOOD_CULTURES", "orderLabel", "Blood cultures", "orderType", "LAB");
        Map<String, Object> item2 = Map.of("orderCode", "LACTATE", "orderLabel", "Serum lactate", "orderType", "LAB");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderSetCode", "SEPSIS_SIX_BUNDLE");
        body.put("orderSetName", "Sepsis Six Bundle");
        body.put("invokedBy", "nurse-A");
        body.put("items", List.of(item1, item2));
        return body;
    }

    @Test
    @DisplayName("invoking a bundle creates the instance and one PENDING item per entry")
    void invokeCreatesInstanceAndItems() {
        var instance = service.invoke(episodeId, TENANT, sepsisBundleBody());
        assertThat(instance.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(instance.getOrderSetCode()).isEqualTo("SEPSIS_SIX_BUNDLE");

        var items = service.items(instance.getInstanceId(), TENANT);
        assertThat(items).hasSize(2);
        assertThat(items).allSatisfy(i -> assertThat(i.getStatus()).isEqualTo("PENDING"));
    }

    @Test
    @DisplayName("invoking against an episode that does not exist in this tenant is refused")
    void invokeRefusesUnknownEpisode() {
        assertThatThrownBy(() -> service.invoke(UUID.randomUUID(), TENANT, sepsisBundleBody()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("ordering an item requires ordered_by")
    void orderRequiresOrderedBy() {
        var instance = service.invoke(episodeId, TENANT, sepsisBundleBody());
        var item = service.items(instance.getInstanceId(), TENANT).get(0);
        assertThatThrownBy(() -> service.orderItem(item.getItemId(), TENANT, Map.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("declining an item requires both declined_by and a reason")
    void declineRequiresReasonAndActor() {
        var instance = service.invoke(episodeId, TENANT, sepsisBundleBody());
        var item = service.items(instance.getInstanceId(), TENANT).get(0);
        assertThatThrownBy(() -> service.declineItem(item.getItemId(), TENANT, Map.of("declinedBy", "Dr X")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.declineItem(item.getItemId(), TENANT, Map.of("reason", "already done")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correctly-declined item is recorded with actor, time and reason")
    void declineSucceedsWithReason() {
        var instance = service.invoke(episodeId, TENANT, sepsisBundleBody());
        var item = service.items(instance.getInstanceId(), TENANT).get(0);
        var declined = service.declineItem(item.getItemId(), TENANT,
                Map.of("declinedBy", "Dr X", "reason", "already drawn on arrival"));
        assertThat(declined.getStatus()).isEqualTo("DECLINED");
        assertThat(declined.getDeclineReason()).isEqualTo("already drawn on arrival");
        assertThat(declined.getDeclinedAt()).isNotNull();
    }

    @Test
    @DisplayName("the instance auto-completes once every item has left PENDING — derived, not caller-set")
    void instanceAutoCompletesWhenAllItemsResolved() {
        var instance = service.invoke(episodeId, TENANT, sepsisBundleBody());
        var items = service.items(instance.getInstanceId(), TENANT);

        service.orderItem(items.get(0).getItemId(), TENANT, Map.of("orderedBy", "Dr X"));
        assertThat(service.getInstance(instance.getInstanceId(), TENANT).getStatus()).isEqualTo("IN_PROGRESS");

        service.declineItem(items.get(1).getItemId(), TENANT, Map.of("declinedBy", "Dr X", "reason", "not indicated"));
        assertThat(service.getInstance(instance.getInstanceId(), TENANT).getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("re-invoking the same bundle code on the same episode is allowed, not a defect")
    void reinvocationIsAllowed() {
        service.invoke(episodeId, TENANT, sepsisBundleBody());
        var second = service.invoke(episodeId, TENANT, sepsisBundleBody());
        assertThat(second).isNotNull();
        assertThat(service.forEpisode(episodeId, TENANT)).hasSize(2);
    }

    /** Minimal in-memory fake — only the methods EmergencyOrderSetService actually calls. */
    private static final class InMemoryInstanceRepo implements EmergencyOrderSetInstanceRepository {
        final Map<UUID, EmergencyOrderSetInstanceEntity> store = new LinkedHashMap<>();

        @Override public Optional<EmergencyOrderSetInstanceEntity> findByInstanceIdAndTenantId(UUID id, UUID tenant) {
            var e = store.get(id);
            return (e != null && e.getTenantId().equals(tenant)) ? Optional.of(e) : Optional.empty();
        }
        @Override public List<EmergencyOrderSetInstanceEntity> findByTenantIdAndEpisodeIdOrderByInvokedAtDesc(UUID tenant, UUID episodeId) {
            List<EmergencyOrderSetInstanceEntity> out = new ArrayList<>();
            for (var e : store.values()) {
                if (e.getTenantId().equals(tenant) && e.getEpisodeId().equals(episodeId)) out.add(e);
            }
            out.sort((a, b) -> b.getInvokedAt().compareTo(a.getInvokedAt()));
            return out;
        }
        @Override public <S extends EmergencyOrderSetInstanceEntity> S save(S e) {
            if (e.getInstanceId() == null) e.setInstanceId(UUID.randomUUID());
            if (e.getInvokedAt() == null) e.setInvokedAt(OffsetDateTime.now());
            store.put(e.getInstanceId(), e);
            return e;
        }
        @Override public <S extends EmergencyOrderSetInstanceEntity> S saveAndFlush(S e) { return save(e); }
        @Override public void flush() { }
        @Override public <S extends EmergencyOrderSetInstanceEntity> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<EmergencyOrderSetInstanceEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public EmergencyOrderSetInstanceEntity getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyOrderSetInstanceEntity getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyOrderSetInstanceEntity getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetInstanceEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetInstanceEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetInstanceEntity> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<EmergencyOrderSetInstanceEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<EmergencyOrderSetInstanceEntity> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<EmergencyOrderSetInstanceEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(EmergencyOrderSetInstanceEntity e) { store.remove(e.getInstanceId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends EmergencyOrderSetInstanceEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public List<EmergencyOrderSetInstanceEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<EmergencyOrderSetInstanceEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetInstanceEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetInstanceEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetInstanceEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetInstanceEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetInstanceEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }

    private static final class InMemoryItemRepo implements EmergencyOrderSetItemRepository {
        final Map<UUID, EmergencyOrderSetItemEntity> store = new LinkedHashMap<>();

        @Override public Optional<EmergencyOrderSetItemEntity> findByItemIdAndTenantId(UUID id, UUID tenant) {
            var e = store.get(id);
            return (e != null && e.getTenantId().equals(tenant)) ? Optional.of(e) : Optional.empty();
        }
        @Override public List<EmergencyOrderSetItemEntity> findByInstanceIdOrderByCreatedAtAsc(UUID instanceId) {
            List<EmergencyOrderSetItemEntity> out = new ArrayList<>();
            for (var e : store.values()) if (e.getInstanceId().equals(instanceId)) out.add(e);
            out.sort((a, b) -> {
                if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                return a.getCreatedAt().compareTo(b.getCreatedAt());
            });
            return out;
        }
        @Override public <S extends EmergencyOrderSetItemEntity> S save(S e) {
            if (e.getItemId() == null) e.setItemId(UUID.randomUUID());
            if (e.getCreatedAt() == null) {
                // mirror @PrePersist since this fake bypasses JPA lifecycle callbacks
                try {
                    var m = EmergencyOrderSetItemEntity.class.getDeclaredMethod("onCreate");
                    m.setAccessible(true);
                    m.invoke(e);
                } catch (ReflectiveOperationException ignored) { /* best-effort */ }
            }
            store.put(e.getItemId(), e);
            return e;
        }
        @Override public <S extends EmergencyOrderSetItemEntity> S saveAndFlush(S e) { return save(e); }
        @Override public void flush() { }
        @Override public <S extends EmergencyOrderSetItemEntity> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<EmergencyOrderSetItemEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public EmergencyOrderSetItemEntity getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyOrderSetItemEntity getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyOrderSetItemEntity getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetItemEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetItemEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetItemEntity> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<EmergencyOrderSetItemEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<EmergencyOrderSetItemEntity> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<EmergencyOrderSetItemEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(EmergencyOrderSetItemEntity e) { store.remove(e.getItemId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends EmergencyOrderSetItemEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public List<EmergencyOrderSetItemEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<EmergencyOrderSetItemEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetItemEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetItemEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetItemEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetItemEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyOrderSetItemEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }
}
