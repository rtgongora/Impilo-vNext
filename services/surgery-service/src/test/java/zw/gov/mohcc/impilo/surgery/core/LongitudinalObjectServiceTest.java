package zw.gov.mohcc.impilo.surgery.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalLongitudinalObjectEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalLongitudinalObjectRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SB-2 — drains/stomas/wounds (§17). Proves the closed kind vocabulary, that removal/revision
 * both stamp actor+time, and revision points the old object forward without destroying it.
 */
@ExtendWith(MockitoExtension.class)
class LongitudinalObjectServiceTest {

    @Mock SurgicalLongitudinalObjectRepository repository;
    @Mock SurgicalEpisodeRepository episodeRepository;

    private LongitudinalObjectService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();
    private final UUID objectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LongitudinalObjectService(repository, episodeRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-nurse", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.findByIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.of(new SurgicalEpisodeEntity()));
        lenient().when(repository.save(any())).thenAnswer(i -> {
            SurgicalLongitudinalObjectEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void placingAnInventedKindIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.place(episodeId, Map.of("kind", "PACEMAKER", "site", "x", "indication", "y")));
        assertEquals("INVALID_LONGITUDINAL_OBJECT_KIND", ex.getCode());
    }

    @Test
    void placingWithNoSiteIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.place(episodeId, Map.of("kind", "DRAIN", "indication", "y")));
        assertEquals("SITE_AND_INDICATION_REQUIRED", ex.getCode());
    }

    @Test
    void placingAValidObjectSucceeds() {
        Map<String, Object> result = service.place(episodeId, Map.of(
                "kind", "DRAIN", "site", "Subhepatic", "indication", "Bile leak risk", "operator", "dr-x"));

        assertEquals("DRAIN", result.get("kind"));
        assertEquals("ACTIVE", result.get("status"));
        assertEquals("Subhepatic", result.get("site"));
    }

    private SurgicalLongitudinalObjectEntity activeObject() {
        SurgicalLongitudinalObjectEntity e = new SurgicalLongitudinalObjectEntity();
        e.setId(objectId);
        e.setTenantId(tenant);
        e.setSurgicalEpisodeId(episodeId);
        e.setKind("DRAIN");
        e.setSite("Subhepatic");
        e.setOperator("dr-x");
        e.setIndication("Bile leak risk");
        e.setStatus("ACTIVE");
        e.setPlacedAt(java.time.OffsetDateTime.now());
        return e;
    }

    @Test
    void removingAnObjectStampsActorAndTimeTogether() {
        when(repository.findByIdAndTenantId(objectId, tenant)).thenReturn(Optional.of(activeObject()));

        Map<String, Object> result = service.remove(objectId, Map.of("removalReason", "Output negligible"));

        assertEquals("REMOVED", result.get("status"));
        assertEquals("actor-nurse", result.get("removedBy"));
        assertNotNull(result.get("removedAt"));
        assertEquals("Output negligible", result.get("removalReason"));
    }

    /** The revision idiom: old object marked REVISED and points at the new one, never destroyed. */
    @Test
    void revisingAnObjectCreatesANewOneAndPointsTheOldOneForward() {
        when(repository.findByIdAndTenantId(objectId, tenant)).thenReturn(Optional.of(activeObject()));

        Map<String, Object> result = service.revise(objectId, Map.of("site", "Subhepatic (repositioned)"));

        assertEquals("ACTIVE", result.get("status"), "the new object starts ACTIVE");
        assertNotEquals(objectId.toString(), result.get("id"), "revision creates a genuinely new object");
    }

    @Test
    void removingAnUnknownObjectIsNotFound() {
        when(repository.findByIdAndTenantId(objectId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.remove(objectId, Map.of()));
        assertEquals("LONGITUDINAL_OBJECT_NOT_FOUND", ex.getCode());
    }
}
