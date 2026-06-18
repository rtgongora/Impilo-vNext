package zw.gov.mohcc.impilo.pct.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import zw.gov.mohcc.impilo.pct.api.dto.CreateImagingLinkRequest;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImagingLinkEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ImagingLinkRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImagingLinkServiceTest {

    @Mock private ImagingLinkRepository imagingLinkRepository;
    @Mock private EncounterRepository encounterRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private final UUID tenantId = UUID.randomUUID();
    private final TrustContext context = new TrustContext(
            tenantId, "actor-1", "PROVIDER", "TREATMENT", null,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);

    @Test
    void createLink_persistsAndWritesOutbox() {
        ImagingLinkService localService = new ImagingLinkService(
                imagingLinkRepository, encounterRepository, outboxRepository, new ObjectMapper());
        EncounterEntity encounter = new EncounterEntity();
        encounter.setId(10L);
        encounter.setTenantId(tenantId);
        encounter.setJourneyId("JOURNEY123456789012345");
        when(encounterRepository.findById(10L)).thenReturn(Optional.of(encounter));
        when(imagingLinkRepository.save(any(ImagingLinkEntity.class))).thenAnswer(invocation -> {
            ImagingLinkEntity saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            ImagingLinkEntity link = localService.createLink(
                    10L,
                    new CreateImagingLinkRequest("42", "OROS-001", "TRIAGE_IMAGING", null));

            assertEquals("42", link.getGovernedStudyId());
            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertEquals("IMAGING_LINK_CREATED", captor.getValue().getEventType());
        }
    }

    @Test
    void listByEncounter_returnsLinks() {
        ImagingLinkService localService = new ImagingLinkService(
                imagingLinkRepository, encounterRepository, outboxRepository, new ObjectMapper());
        EncounterEntity encounter = new EncounterEntity();
        encounter.setId(10L);
        encounter.setTenantId(tenantId);
        when(encounterRepository.findById(10L)).thenReturn(Optional.of(encounter));
        ImagingLinkEntity link = new ImagingLinkEntity();
        link.setGovernedStudyId("42");
        when(imagingLinkRepository.findByTenantIdAndEncounterIdOrderByCreatedAtDesc(tenantId, 10L))
                .thenReturn(List.of(link));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            List<ImagingLinkEntity> results = localService.listByEncounter(10L);
            assertEquals(1, results.size());
            assertEquals("42", results.get(0).getGovernedStudyId());
        }
    }
}
