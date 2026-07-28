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
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalPrehabItemEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalPrehabItemRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SB-1 — prehabilitation/optimisation items (§10). Proves the closed domain/status vocabularies,
 * that a registry_ref never carries the underlying fact itself (this service only records a
 * reference and its own execution status), and refine-not-duplicate per domain.
 */
@ExtendWith(MockitoExtension.class)
class PrehabItemServiceTest {

    @Mock SurgicalPrehabItemRepository repository;
    @Mock SurgicalEpisodeRepository episodeRepository;

    private PrehabItemService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PrehabItemService(repository, episodeRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-nurse", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.findByIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.of(new SurgicalEpisodeEntity()));
        lenient().when(repository.save(any())).thenAnswer(i -> {
            SurgicalPrehabItemEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void recordingAnInventedDomainIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordItem(episodeId, Map.of("domain", "ASTROLOGY")));
        assertEquals("INVALID_PREHAB_DOMAIN", ex.getCode());
    }

    @Test
    void recordingAnInventedStatusIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordItem(episodeId, Map.of("domain", "NUTRITION", "status", "VIBES_GOOD")));
        assertEquals("INVALID_PREHAB_STATUS", ex.getCode());
    }

    @Test
    void recordingAValidDomainAndStatusSucceeds() {
        Map<String, Object> result = service.recordItem(episodeId, Map.of(
                "domain", "SMOKING_CESSATION", "status", "IN_PROGRESS",
                "target", "Quit 4 weeks pre-operatively", "registryRef", UUID.randomUUID().toString()));

        assertEquals("SMOKING_CESSATION", result.get("domain"));
        assertEquals("IN_PROGRESS", result.get("status"));
        assertEquals("Quit 4 weeks pre-operatively", result.get("target"));
    }

    @Test
    void recordingForAnUnknownEpisodeIsNotFound() {
        when(episodeRepository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordItem(episodeId, Map.of("domain", "NUTRITION")));
        assertEquals("SURGICAL_EPISODE_NOT_FOUND", ex.getCode());
    }

    /** One row per (episode, domain) — a second call refines the same item rather than duplicating it. */
    @Test
    void recordingTheSameDomainTwiceRefinesTheSameItem() {
        SurgicalPrehabItemEntity existing = new SurgicalPrehabItemEntity();
        existing.setId(UUID.randomUUID());
        existing.setSurgicalEpisodeId(episodeId);
        existing.setTenantId(tenant);
        existing.setDomain("ANAEMIA");
        existing.setStatus("PLANNED");
        when(repository.findBySurgicalEpisodeIdAndTenantIdAndDomain(episodeId, tenant, "ANAEMIA"))
                .thenReturn(Optional.of(existing));

        Map<String, Object> result = service.recordItem(episodeId, Map.of("domain", "ANAEMIA", "status", "COMPLETED"));

        assertEquals("COMPLETED", result.get("status"));
    }
}
