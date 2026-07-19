package zw.gov.mohcc.impilo.vito.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.core.did.SovereignIdGenerator;
import zw.gov.mohcc.impilo.vito.core.matching.MatchingEngine;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Person-scoped dedupe on the golden registration path: a blind repeat of the
 * same registration (double submit, stateless retry) must return the existing
 * client instead of minting a second PROVISIONAL person.
 */
@ExtendWith(MockitoExtension.class)
class IdentityServiceDedupeTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private ClientRepository clientRepository;
    @Mock private SovereignIdGenerator didGenerator;
    @Mock private ImpiloIdAliasService aliasService;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private MatchingEngine matchingEngine;

    private IdentityService service;
    private ClientEntity existing;

    @BeforeEach
    void setUp() {
        service = new IdentityService(clientRepository, didGenerator, aliasService,
                outboxRepository, matchingEngine, new VitoProperties());
        existing = new ClientEntity();
        existing.setTenantId(TENANT);
        existing.setHealthId(UUID.randomUUID());
        existing.setGivenName("Test");
        existing.setFamilyName("Registrant");
        existing.setDateOfBirth(LocalDate.parse("1990-01-01"));
        existing.setSex("F");
        existing.setStatus(IdentityStatus.PROVISIONAL);
    }

    @Test
    @DisplayName("identical demographics return the existing client — no new row, no IDENTITY_CREATED")
    void strongMatchReturnsExistingClient() {
        when(clientRepository.findBlockingCandidates(eq(TENANT), any(), eq(1990), eq("r")))
                .thenReturn(List.of(existing));
        when(matchingEngine.scoreDemographics("Test", "Registrant", "1990-01-01", "F", existing))
                .thenReturn(1.0);

        IdentityService.ClientRegistrationResult result =
                service.register(TENANT, "Test", "Registrant", "1990-01-01", "F", "rig");

        assertSame(existing, result.client(), "must return the existing client");
        assertNull(result.did(), "no DID is fabricated for an existing person");
        verify(clientRepository, never()).save(any());
        verify(didGenerator, never()).generate(any());

        ArgumentCaptor<EventOutboxEntity> event = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(event.capture());
        assertEquals("IDENTITY_DEDUPLICATED", event.getValue().getEventType());
    }

    @Test
    @DisplayName("below the auto-link threshold a new PROVISIONAL client is minted")
    void weakMatchMintsNewClient() {
        when(clientRepository.findBlockingCandidates(eq(TENANT), any(), eq(1990), eq("r")))
                .thenReturn(List.of(existing));
        when(matchingEngine.scoreDemographics(anyString(), anyString(), anyString(), anyString(), eq(existing)))
                .thenReturn(0.60);
        when(clientRepository.save(any())).thenAnswer(inv -> {
            ClientEntity c = inv.getArgument(0);
            c.setHealthId(UUID.randomUUID());
            return c;
        });
        when(didGenerator.generate(any())).thenReturn("did:impilo:new");

        IdentityService.ClientRegistrationResult result =
                service.register(TENANT, "Tessa", "Registrar", "1990-05-05", "F", "rig");

        assertNotSame(existing, result.client());
        assertEquals("did:impilo:new", result.did());
        verify(clientRepository).save(any());
    }

    @Test
    @DisplayName("MERGED and DECEASED records never dedupe")
    void tombstonedRecordsNeverDedupe() {
        existing.setStatus(IdentityStatus.MERGED);
        when(clientRepository.findBlockingCandidates(eq(TENANT), any(), eq(1990), eq("r")))
                .thenReturn(List.of(existing));
        when(clientRepository.save(any())).thenAnswer(inv -> {
            ClientEntity c = inv.getArgument(0);
            c.setHealthId(UUID.randomUUID());
            return c;
        });
        when(didGenerator.generate(any())).thenReturn("did:impilo:new");

        IdentityService.ClientRegistrationResult result =
                service.register(TENANT, "Test", "Registrant", "1990-01-01", "F", "rig");

        assertNotSame(existing, result.client(), "a tombstone must not absorb a registration");
        verify(matchingEngine, never()).scoreDemographics(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("no blocking key (no family name, no DOB) skips dedupe and mints")
    void noBlockingKeySkipsDedupe() {
        when(clientRepository.save(any())).thenAnswer(inv -> {
            ClientEntity c = inv.getArgument(0);
            c.setHealthId(UUID.randomUUID());
            return c;
        });
        when(didGenerator.generate(any())).thenReturn("did:impilo:new");

        service.register(TENANT, "OnlyGiven", null, null, "M", "rig");

        verify(clientRepository, never()).findBlockingCandidates(any(), any(), any(), any());
        verify(clientRepository).save(any());
    }
}
