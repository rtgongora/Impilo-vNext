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
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalWaitlistRevalidationEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalWaitlistRevalidationRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SB-2 — waiting-list clinical revalidation (§9, surgery's own slice only). Proves the
 * append-only shape and that "still clinically appropriate" is never assumed.
 */
@ExtendWith(MockitoExtension.class)
class WaitlistRevalidationServiceTest {

    @Mock SurgicalWaitlistRevalidationRepository repository;
    @Mock SurgicalEpisodeRepository episodeRepository;

    private WaitlistRevalidationService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WaitlistRevalidationService(repository, episodeRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.findByIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.of(new SurgicalEpisodeEntity()));
        lenient().when(repository.save(any())).thenAnswer(i -> {
            SurgicalWaitlistRevalidationEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void revalidatingWithNoAppropriatenessFlagIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.revalidate(episodeId, Map.of("notes", "seen in clinic")));
        assertEquals("STILL_CLINICALLY_APPROPRIATE_REQUIRED", ex.getCode());
    }

    @Test
    void revalidatingWithAnExplicitFlagSucceeds() {
        Map<String, Object> result = service.revalidate(episodeId, Map.of(
                "stillClinicallyAppropriate", true, "nextRevalidationDue", "2026-10-01"));

        assertEquals(true, result.get("stillClinicallyAppropriate"));
        assertEquals("actor-surgeon", result.get("revalidatedBy"));
        assertEquals("2026-10-01", result.get("nextRevalidationDue"));
    }

    @Test
    void revalidatingForAnUnknownEpisodeIsNotFound() {
        when(episodeRepository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.revalidate(episodeId, Map.of("stillClinicallyAppropriate", false)));
        assertEquals("SURGICAL_EPISODE_NOT_FOUND", ex.getCode());
    }

    /** Append-only: each revalidation is its own row, not an update to a prior one. */
    @Test
    void multipleRevalidationsAreListedMostRecentFirst() {
        SurgicalWaitlistRevalidationEntity older = new SurgicalWaitlistRevalidationEntity();
        older.setId(UUID.randomUUID());
        older.setSurgicalEpisodeId(episodeId);
        older.setRevalidatedAt(java.time.OffsetDateTime.now().minusDays(30));
        SurgicalWaitlistRevalidationEntity newer = new SurgicalWaitlistRevalidationEntity();
        newer.setId(UUID.randomUUID());
        newer.setSurgicalEpisodeId(episodeId);
        newer.setRevalidatedAt(java.time.OffsetDateTime.now());
        when(repository.findBySurgicalEpisodeIdAndTenantIdOrderByRevalidatedAtDesc(episodeId, tenant))
                .thenReturn(List.of(newer, older));

        List<Map<String, Object>> result = service.listForEpisode(episodeId);

        assertEquals(2, result.size());
        assertEquals(newer.getId().toString(), result.get(0).get("id"));
    }
}
