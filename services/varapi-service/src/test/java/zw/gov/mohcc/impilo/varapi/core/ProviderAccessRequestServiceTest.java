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

import java.util.List;
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

    // ── Reviewer lane (IATG Trust Console) ─────────────────────────────────────

    private ProviderAccessRequestEntity pendingRequest(String status) {
        ProviderAccessRequestEntity e = new ProviderAccessRequestEntity();
        e.setPublicId("PAR-REVIEW01");
        e.setTenantId(TENANT);
        e.setApplicantHealthId(APPLICANT);
        e.setRequestType("NEW_PROVIDER");
        e.setStatus(status);
        e.setNextActor("NATIONAL_ADMINISTRATOR");
        return e;
    }

    @Test
    void decideApprovesFromPendingReviewAndRecordsReviewer() {
        withContext();
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-REVIEW01"))
                .thenReturn(Optional.of(pendingRequest("PENDING_NATIONAL_REVIEW")));

        ProviderAccessRequestEntity e = service().decide("PAR-REVIEW01", "APPROVED", "Council registration confirmed");

        assertEquals("APPROVED", e.getStatus());
        assertEquals(APPLICANT.toString(), e.getDecidedBy());
        assertNotNull(e.getDecidedAt());
        assertEquals("Council registration confirmed", e.getDecisionNote());
        assertNull(e.getNextActor());
        verify(outboxRepository).save(any());
    }

    @Test
    void decideNeedsMoreInformationRoutesBackToApplicant() {
        withContext();
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-REVIEW01"))
                .thenReturn(Optional.of(pendingRequest("SUBMITTED")));

        ProviderAccessRequestEntity e = service().decide(
                "PAR-REVIEW01", "needs_more_information", "Attach the council certificate");

        assertEquals("NEEDS_MORE_INFORMATION", e.getStatus());
        assertEquals("APPLICANT", e.getNextActor());
        assertEquals("Attach the council certificate", e.getReason());
    }

    @Test
    void decideRejectsUnknownDecisionValue() {
        withContext();
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-REVIEW01"))
                .thenReturn(Optional.of(pendingRequest("PENDING_COUNCIL_REVIEW")));

        assertThrows(IllegalArgumentException.class,
                () -> service().decide("PAR-REVIEW01", "ESCALATED", null));
    }

    @Test
    void decideRefusesTerminalStatuses() {
        withContext();
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-REVIEW01"))
                .thenReturn(Optional.of(pendingRequest("APPROVED")));

        assertThrows(IllegalStateException.class,
                () -> service().decide("PAR-REVIEW01", "REJECTED", "flip-flop"));
    }

    @Test
    void decideIsTenantScoped() {
        withContext();
        // The repository lookup is tenant-keyed: another tenant's publicId resolves to empty.
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-OTHERTNT"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service().decide("PAR-OTHERTNT", "APPROVED", null));
    }

    @Test
    void listForReviewDefaultsToDecidableStatusesForTenant() {
        withContext();
        when(requestRepository.findByTenantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(pendingRequest("PENDING_NATIONAL_REVIEW")));

        List<ProviderAccessRequestEntity> rows = service().listForReview(null);

        assertEquals(1, rows.size());
        verify(requestRepository).findByTenantIdAndStatusInOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT),
                org.mockito.ArgumentMatchers.argThat(statuses ->
                        statuses.containsAll(ProviderAccessRequestService.DECIDABLE_STATUSES)
                                && !statuses.contains("APPROVED")));
    }

    @Test
    void listForReviewNormalisesExplicitStatuses() {
        withContext();
        when(requestRepository.findByTenantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());

        service().listForReview(List.of("pending_national_review", " submitted "));

        verify(requestRepository).findByTenantIdAndStatusInOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT),
                org.mockito.ArgumentMatchers.eq(List.of("PENDING_NATIONAL_REVIEW", "SUBMITTED")));
    }

    @Test
    void nonUuidActorIsRejected() {
        TrustContextHolder.set(new TrustContext(TENANT, "not-a-uuid", "PERSON", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        assertThrows(IllegalArgumentException.class, () -> service().submit(
                new SubmitProviderAccessRequest("NEW_PROVIDER", null, null, null, null, null, null)));
    }
}
