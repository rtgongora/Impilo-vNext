package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.SubmitProviderAccessRequest;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAccessRequestEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderAccessRequestRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProviderAccessRequestServiceTest {

    @Mock ProviderAccessRequestRepository requestRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock ProviderRepository providerRepository;

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID APPLICANT = UUID.fromString("b0000000-0000-4000-8000-000000000001");

    private ProviderAccessRequestService service() {
        return new ProviderAccessRequestService(requestRepository, outboxRepository, providerRepository);
    }

    private void withContext() {
        TrustContextHolder.set(new TrustContext(TENANT, APPLICANT.toString(), "PERSON", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        when(requestRepository.existsByPublicId(anyString())).thenReturn(false);
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        TrustContextHolder.clear();
    }

    @Test
    void newProviderWithoutEvidenceRoutesToNationalReview() {
        withContext();
        when(providerRepository.findByTenantIdAndImpiloHealthId(TENANT, APPLICANT)).thenReturn(Optional.empty());

        ProviderAccessRequestEntity e = service().submit(new SubmitProviderAccessRequest(
                "NEW_PROVIDER", "Medical Officer", null, null, null, null, null));

        assertEquals("NEW_PROVIDER", e.getRequestType());
        assertEquals("PENDING_NATIONAL_REVIEW", e.getStatus());
        assertEquals("NATIONAL_ADMINISTRATOR", e.getNextActor());
        assertEquals(APPLICANT, e.getApplicantHealthId());
        assertTrue(e.getPublicId().startsWith("PAR-"));
        assertNotNull(e.getReason());
        verify(outboxRepository).save(any());
    }

    @Test
    void newProviderWithCouncilNumberRoutesToCouncilAndMasksIt() {
        withContext();
        when(providerRepository.findByTenantIdAndImpiloHealthId(TENANT, APPLICANT)).thenReturn(Optional.empty());

        ProviderAccessRequestEntity e = service().submit(new SubmitProviderAccessRequest(
                "NEW_PROVIDER", "Doctor", "MDPCZ", "MDPCZ-123456", null, null, null));

        assertEquals("PENDING_COUNCIL_REVIEW", e.getStatus());
        assertEquals("COUNCIL_REVIEWER", e.getNextActor());
        // masked: first four + *** — the raw council number is never persisted here.
        assertEquals("MDPC***", e.getCouncilNumberMasked());
    }

    @Test
    void orgInvitationRoutesToOrganizationReview() {
        withContext();
        when(providerRepository.findByTenantIdAndImpiloHealthId(TENANT, APPLICANT)).thenReturn(Optional.empty());

        ProviderAccessRequestEntity e = service().submit(new SubmitProviderAccessRequest(
                "ORG_INVITATION", null, null, null, null, "Chitungwiza Mission", null));

        assertEquals("PENDING_ORGANIZATION_REVIEW", e.getStatus());
        assertEquals("ORGANIZATION_REPRESENTATIVE", e.getNextActor());
        assertEquals("Chitungwiza Mission", e.getOrganizationRef());
    }

    @Test
    void newProviderWhenAlreadyLinkedIsDuplicateSuspectedNotReissued() {
        withContext();
        when(providerRepository.findByTenantIdAndImpiloHealthId(TENANT, APPLICANT))
                .thenReturn(Optional.of(new ProviderEntity()));

        ProviderAccessRequestEntity e = service().submit(new SubmitProviderAccessRequest(
                "NEW_PROVIDER", "Doctor", null, null, null, null, null));

        assertEquals("DUPLICATE_SUSPECTED", e.getStatus());
        assertEquals("NATIONAL_ADMINISTRATOR", e.getNextActor());
        assertTrue(e.getReason().toLowerCase().contains("recover"));
    }

    @Test
    void nonUuidActorIsRejected() {
        TrustContextHolder.set(new TrustContext(TENANT, "not-a-uuid", "PERSON", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        assertThrows(IllegalArgumentException.class, () -> service().submit(
                new SubmitProviderAccessRequest("NEW_PROVIDER", null, null, null, null, null, null)));
    }
}
