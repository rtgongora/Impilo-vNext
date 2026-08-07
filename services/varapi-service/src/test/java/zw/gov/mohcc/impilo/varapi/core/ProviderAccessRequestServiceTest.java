package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProviderAccessRequestServiceTest {

    @Mock ProviderAccessRequestRepository requestRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock ProviderRepository providerRepository;
    @Mock ProviderBootstrapService bootstrapService;

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID APPLICANT = UUID.fromString("b0000000-0000-4000-8000-000000000001");

    private ProviderAccessRequestService service() {
        return new ProviderAccessRequestService(requestRepository, outboxRepository, providerRepository, bootstrapService);
    }

    private static final UUID REVIEWER = UUID.randomUUID();

    private void withContext() {
        TrustContextHolder.set(new TrustContext(TENANT, APPLICANT.toString(), "PERSON", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        when(requestRepository.existsByPublicId(anyString())).thenReturn(false);
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * A reviewer session. The applicant context above is a PERSON, which is now refused on the
     * reviewer lane — reading the queue or deciding a request requires a reviewer actor type,
     * because an approval binds a professional identity to a person.
     */
    private void withReviewerContext() {
        withContext();
        TrustContextHolder.set(new TrustContext(TENANT, REVIEWER.toString(), "OPERATOR", "OPERATIONS",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
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
                "NEW_PROVIDER", "Medical Officer", null, null, null, null, null, null, null, null, null));

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
                "NEW_PROVIDER", "Doctor", "MDPCZ", "MDPCZ-123456", null, null, null, null, null, null, null));

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
                "ORG_INVITATION", null, null, null, null, "Chitungwiza Mission", null, null, null, null, null));

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
                "NEW_PROVIDER", "Doctor", null, null, null, null, null, null, null, null, null));

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
        withReviewerContext();
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-REVIEW01"))
                .thenReturn(Optional.of(pendingRequest("PENDING_NATIONAL_REVIEW")));

        ProviderAccessRequestEntity e = service().decide("PAR-REVIEW01", "APPROVED", "Council registration confirmed");

        assertEquals("APPROVED", e.getStatus());
        assertEquals(REVIEWER.toString(), e.getDecidedBy());
        assertNotNull(e.getDecidedAt());
        assertEquals("Council registration confirmed", e.getDecisionNote());
        assertNull(e.getNextActor());
        verify(outboxRepository).save(any());
    }

    @Test
    void decideNeedsMoreInformationRoutesBackToApplicant() {
        withReviewerContext();
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
        withReviewerContext();
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-REVIEW01"))
                .thenReturn(Optional.of(pendingRequest("PENDING_COUNCIL_REVIEW")));

        assertThrows(IllegalArgumentException.class,
                () -> service().decide("PAR-REVIEW01", "ESCALATED", null));
    }

    @Test
    void decideRefusesTerminalStatuses() {
        withReviewerContext();
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-REVIEW01"))
                .thenReturn(Optional.of(pendingRequest("APPROVED")));

        assertThrows(IllegalStateException.class,
                () -> service().decide("PAR-REVIEW01", "REJECTED", "flip-flop"));
    }

    @Test
    void decideIsTenantScoped() {
        withReviewerContext();
        // The repository lookup is tenant-keyed: another tenant's publicId resolves to empty.
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-OTHERTNT"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service().decide("PAR-OTHERTNT", "APPROVED", null));
    }

    @Test
    void listForReviewDefaultsToDecidableStatusesForTenant() {
        withReviewerContext();
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
        withReviewerContext();
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
                new SubmitProviderAccessRequest("NEW_PROVIDER", null, null, null, null, null, null, null, null, null, null)));
    }

    // ── Approving a council-number claim must actually grant it ─────────────────────────────
    // Before this, decide() moved the request to APPROVED, published the event, and never touched
    // varapi.provider. claimed_at is what booking gates on, so the practitioner stayed unbookable
    // while the console showed them approved. Measured at the time: 4,268 providers, zero claimed.

    private ProviderAccessRequestEntity councilClaim(String status, String providerPublicId) {
        ProviderAccessRequestEntity e = new ProviderAccessRequestEntity();
        e.setPublicId("PAR-CLAIM01");
        e.setTenantId(TENANT);
        e.setApplicantHealthId(APPLICANT);
        e.setRequestType("COUNCIL_NUMBER");
        e.setStatus(status);
        e.setProviderPublicId(providerPublicId);
        return e;
    }

    @Test
    void approvingACouncilNumberClaimCompletesTheClaim() {
        withReviewerContext();
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-CLAIM01"))
                .thenReturn(Optional.of(councilClaim("PENDING_NATIONAL_REVIEW", "PRV-0001")));

        ProviderAccessRequestEntity e = service().decide("PAR-CLAIM01", "APPROVED", "Verified against the register");

        assertEquals("APPROVED", e.getStatus());
        // The binding is the point of the approval, and it carries the request id so the link
        // records which decision authorised it.
        verify(bootstrapService).completeClaimByReview("PRV-0001", APPLICANT, "PAR-CLAIM01");
    }

    @Test
    void approvingAnUnmatchedCouncilEnquiryBindsNothing() {
        withReviewerContext();
        // HpaPractitionerClaimService leaves providerPublicId null when the number matched no
        // preloaded HPA row. Approving that is a legitimate new-registration enquiry, not a claim.
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-CLAIM01"))
                .thenReturn(Optional.of(councilClaim("PENDING_NATIONAL_REVIEW", null)));

        ProviderAccessRequestEntity e = service().decide("PAR-CLAIM01", "APPROVED", null);

        assertEquals("APPROVED", e.getStatus());
        verifyNoInteractions(bootstrapService);
    }

    @Test
    void rejectingACouncilNumberClaimBindsNothing() {
        withReviewerContext();
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-CLAIM01"))
                .thenReturn(Optional.of(councilClaim("PENDING_NATIONAL_REVIEW", "PRV-0001")));

        service().decide("PAR-CLAIM01", "REJECTED", "Could not verify identity");

        verifyNoInteractions(bootstrapService);
    }

    @Test
    void aFailedBindingKeepsTheVerdictAndSaysWhyInsteadOfReadingAsGranted() {
        withReviewerContext();
        when(requestRepository.findByTenantIdAndPublicId(TENANT, "PAR-CLAIM01"))
                .thenReturn(Optional.of(councilClaim("PENDING_NATIONAL_REVIEW", "PRV-0001")));
        doThrow(new IllegalStateException("This person already has a provider profile"))
                .when(bootstrapService).completeClaimByReview(anyString(), any(), anyString());

        ProviderAccessRequestEntity e = service().decide("PAR-CLAIM01", "APPROVED", null);

        // The silent-success failure mode is the one that matters: a request that reads APPROVED
        // while nothing was granted. It goes back to a human, carrying the reason.
        assertEquals("NEEDS_MORE_INFORMATION", e.getStatus());
        assertEquals("REVIEWER", e.getNextActor());
        assertTrue(e.getReason().contains("already has a provider profile"));
    }

    // ── The reviewer lane is authorised in-service, not only at the gateway ──────────────────
    // The ext_authz rules (trust-console-varapi-review-system-admin / -hie-admin) are real, but
    // they only cover traffic routed through Envoy. Anything reaching this service inside the
    // cluster arrived unchecked — and an APPROVED council-number claim now binds a provider
    // profile to a person's Health ID, which is the strongest thing this service grants.

    @Test
    void decideRefusesAnActorWhoIsNotAReviewer() {
        withContext();   // an ordinary signed-in PERSON — the applicant, not a reviewer
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service().decide("PAR-REVIEW01", "APPROVED", null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        // Refused before the request is loaded, so an unauthorised caller cannot use this to learn
        // whether a given public id exists.
        verify(requestRepository, never()).findByTenantIdAndPublicId(any(), anyString());
    }

    @Test
    void decideRefusesWhenNoActorTypeIsPresentAtAll() {
        TrustContextHolder.set(new TrustContext(TENANT, APPLICANT.toString(), null, "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service().decide("PAR-REVIEW01", "APPROVED", null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        // The message has to name what is required: the first person to hit this will be a
        // reviewer whose identity was provisioned without a matching actor type. It names the
        // ENFORCED set, not the recorded intent — REGISTRY_ADMIN is a role no client emits, so
        // telling a refused reviewer to obtain it would send them somewhere that leads nowhere.
        assertTrue(ex.getReason().contains("OPERATOR"), ex.getReason());
    }

    @Test
    void theReviewQueueIsNotApplicantFacing() {
        withContext();
        // The queue is tenant-wide and carries other people's applicant Health IDs, masked
        // council numbers and evidence summaries.
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service().listForReview(null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void aPersonMayStillSeeAndSubmitTheirOwnRequests() {
        withContext();
        // The guard is on the reviewer lane only. Self-service must be untouched, or the fix
        // would have broken the very journey it exists to protect.
        assertDoesNotThrow(() -> service().listForApplicant());
    }
}
