package zw.gov.mohcc.impilo.wallet.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.wallet.core.FundingService;
import zw.gov.mohcc.impilo.wallet.core.WalletService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionRequestEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.DepositIntentEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.BillContributionRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.BillContributionRequestRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.EventOutboxRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillContributionServiceTest {

    private BillContributionRequestRepository requestRepo;
    private BillContributionRepository contribRepo;
    private WalletService walletService;
    private FundingService fundingService;
    private EventOutboxRepository outboxRepo;
    private BillContributionService service;
    private final UUID walletId = UUID.randomUUID();
    private final UUID escrowWalletId = UUID.randomUUID();
    private final UUID beneficiaryWalletId = UUID.randomUUID();
    private final UUID donorWalletId = UUID.randomUUID();
    private final UUID campaignRef = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        requestRepo = mock(BillContributionRequestRepository.class);
        contribRepo = mock(BillContributionRepository.class);
        walletService = mock(WalletService.class);
        fundingService = mock(FundingService.class);
        outboxRepo = mock(EventOutboxRepository.class);
        service = new BillContributionService(requestRepo, contribRepo, walletService,
                fundingService, outboxRepo, new ObjectMapper());
        when(requestRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(contribRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(contribRepo.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(walletService.credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new TransactionEntity());
        when(walletService.transfer(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new TransactionEntity());
    }

    private BillContributionRequestEntity openRequest(BigDecimal target, BigDecimal raised) {
        BillContributionRequestEntity r = new BillContributionRequestEntity();
        r.setId(UUID.randomUUID());
        r.setShareToken("tok-1");
        r.setBeneficiaryWalletId(walletId);
        r.setTitle("Help with my hospital bill");
        r.setTargetAmount(target);
        r.setRaisedAmount(raised);
        r.setStatus("OPEN");
        return r;
    }

    private BillContributionRequestEntity openCampaignRequest() {
        BillContributionRequestEntity r = openRequest(new BigDecimal("500.00"), BigDecimal.ZERO);
        r.setOrigin(BillContributionRequestEntity.ORIGIN_CAMPAIGN);
        r.setCampaignRef(campaignRef);
        r.setBeneficiaryWalletId(escrowWalletId);
        r.setBeneficiaryTargetWalletId(beneficiaryWalletId);
        r.setTenantId(UUID.randomUUID());
        return r;
    }

    @Test
    void create_generates_a_share_token() {
        var r = service.createRequest(UUID.randomUUID(), walletId, "Bill", "BILL-9",
                new BigDecimal("100.00"), "USD", "cpid-1");
        assertNotNull(r.getShareToken());
        assertEquals("OPEN", r.getStatus());
        assertEquals(BillContributionRequestEntity.ORIGIN_BILL, r.getOrigin());
    }

    @Test
    void campaign_create_creates_escrow_wallet_and_targets_real_beneficiary() {
        WalletEntity escrow = new WalletEntity();
        escrow.setWalletId(escrowWalletId);
        when(walletService.createWallet(any(), eq(BillContributionService.ESCROW_OWNER_TYPE),
                eq(campaignRef.toString()), any(), eq("USD"))).thenReturn(escrow);

        var r = service.createRequest(UUID.randomUUID(), null, "Surgery fund", null,
                new BigDecimal("500.00"), "USD", "daidzai",
                BillContributionRequestEntity.ORIGIN_CAMPAIGN, campaignRef, beneficiaryWalletId);

        assertEquals(escrowWalletId, r.getBeneficiaryWalletId());
        assertEquals(beneficiaryWalletId, r.getBeneficiaryTargetWalletId());
        assertEquals(campaignRef, r.getCampaignRef());
        assertEquals(BillContributionRequestEntity.ORIGIN_CAMPAIGN, r.getOrigin());
    }

    @Test
    void campaign_create_without_campaignRef_or_target_wallet_is_rejected() {
        assertThrows(BillContributionService.ContributionRejected.class,
                () -> service.createRequest(UUID.randomUUID(), null, "t", null, null, "USD", "x",
                        BillContributionRequestEntity.ORIGIN_CAMPAIGN, null, beneficiaryWalletId));
        assertThrows(BillContributionService.ContributionRejected.class,
                () -> service.createRequest(UUID.randomUUID(), null, "t", null, null, "USD", "x",
                        BillContributionRequestEntity.ORIGIN_CAMPAIGN, campaignRef, null));
    }

    @Test
    void contribute_credits_the_beneficiary_wallet_and_advances_the_total() {
        var request = openRequest(new BigDecimal("100.00"), BigDecimal.ZERO);
        request.setTenantId(UUID.randomUUID());
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));

        BillContributionEntity c = service.contribute("tok-1", new BigDecimal("30.00"),
                "cpid-2", "Aunty Rudo", "Get well soon", "idem-1");

        verify(walletService).credit(eq(walletId), eq(new BigDecimal("30.00")), eq("BILL_CONTRIBUTION"),
                eq("SOCIAL_FUNDING"), eq("tok-1"), any(), eq("cpid-2"), eq("Aunty Rudo"), any(), eq("idem-1"));
        assertEquals(new BigDecimal("30.00"), request.getRaisedAmount());
        assertEquals("OPEN", request.getStatus());
        assertEquals("Aunty Rudo", c.getContributorName());
    }

    @Test
    void wallet_contribution_transfers_donor_to_escrow_under_the_contribution_key() {
        var request = openCampaignRequest();
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));

        BillContributionEntity c = service.contribute("tok-1", new BigDecimal("25.00"),
                "cpid-9", "Donor", null, "idem-w1", donorWalletId, null, false);

        verify(walletService).transfer(eq(donorWalletId), eq(escrowWalletId), eq(new BigDecimal("25.00")),
                eq("tok-1"), any(), eq("SOCIAL_FUNDING"), any(), eq("idem-w1"));
        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertEquals(BillContributionEntity.ORIGIN_WALLET, c.getOrigin());
        assertEquals(donorWalletId, c.getContributorWalletId());
        assertEquals(new BigDecimal("25.00"), request.getRaisedAmount());
    }

    @Test
    void replayed_wallet_contribution_transfers_exactly_once() {
        var request = openCampaignRequest();
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));
        BillContributionEntity prior = new BillContributionEntity();
        when(contribRepo.findByIdempotencyKey("idem-dup")).thenReturn(Optional.of(prior));

        BillContributionEntity replay = service.contribute("tok-1", new BigDecimal("25.00"),
                "cpid-9", "Donor", null, "idem-dup", donorWalletId, null, false);

        assertEquals(prior, replay);
        verify(walletService, never()).transfer(any(), any(), any(), any(), any(), any(), any(), any());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void insufficient_donor_funds_reject_cleanly_with_no_partial_state() {
        var request = openCampaignRequest();
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));
        when(walletService.transfer(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Insufficient available balance"));

        assertThrows(BillContributionService.ContributionRejected.class,
                () -> service.contribute("tok-1", new BigDecimal("999.00"),
                        "cpid-9", "Donor", null, "idem-poor", donorWalletId, null, false));

        verify(contribRepo, never()).save(any());
        verify(outboxRepo, never()).save(any());
        assertEquals(BigDecimal.ZERO, request.getRaisedAmount());
    }

    @Test
    void cash_assisted_contribution_still_bare_credits_the_escrow_and_is_flagged() {
        var request = openCampaignRequest();
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));

        BillContributionEntity c = service.contribute("tok-1", new BigDecimal("10.00"),
                "cpid-3", "Desk", null, "idem-cash", null,
                BillContributionEntity.ORIGIN_CASH_ASSISTED, false);

        verify(walletService).credit(eq(escrowWalletId), eq(new BigDecimal("10.00")), eq("BILL_CONTRIBUTION"),
                eq("SOCIAL_FUNDING"), eq("tok-1"), any(), eq("cpid-3"), eq("Desk"), any(), eq("idem-cash"));
        assertEquals(BillContributionEntity.ORIGIN_CASH_ASSISTED, c.getOrigin());
    }

    @Test
    void campaign_contribution_without_wallet_and_not_cash_assisted_is_rejected() {
        var request = openCampaignRequest();
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));

        assertThrows(BillContributionService.ContributionRejected.class,
                () -> service.contribute("tok-1", new BigDecimal("10.00"),
                        "cpid-3", "x", null, "idem-bare", null, null, false));
        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).transfer(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void contribution_that_meets_target_fulfils_the_request() {
        var request = openRequest(new BigDecimal("100.00"), new BigDecimal("80.00"));
        request.setTenantId(UUID.randomUUID());
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));

        service.contribute("tok-1", new BigDecimal("20.00"), "cpid-3", "Group", null, "idem-2");

        assertEquals(new BigDecimal("100.00"), request.getRaisedAmount());
        assertEquals("FULFILLED", request.getStatus());
    }

    @Test
    void contribution_to_a_closed_request_is_rejected_and_does_not_credit() {
        var request = openRequest(new BigDecimal("100.00"), BigDecimal.ZERO);
        request.setStatus("CLOSED");
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));

        assertThrows(BillContributionService.ContributionRejected.class,
                () -> service.contribute("tok-1", new BigDecimal("10.00"), "x", "y", null, "idem-3"));
        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void replayed_contribution_does_not_double_credit() {
        var request = openRequest(new BigDecimal("100.00"), BigDecimal.ZERO);
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));
        BillContributionEntity prior = new BillContributionEntity();
        when(contribRepo.findByIdempotencyKey("idem-dup")).thenReturn(Optional.of(prior));

        service.contribute("tok-1", new BigDecimal("10.00"), "x", "y", null, "idem-dup");

        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void psp_donate_raises_a_pending_crowdfunding_intent_against_the_escrow() {
        var request = openCampaignRequest();
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));
        DepositIntentEntity pending = new DepositIntentEntity();
        when(fundingService.requestPurposedDeposit(eq(escrowWalletId), eq("MOBILE_MONEY"),
                eq(new BigDecimal("15.00")), eq("USD"), eq(DepositIntentEntity.PURPOSE_CROWDFUNDING),
                eq("tok-1"), any(), any())).thenReturn(pending);

        var intent = service.pspDonate("tok-1", new BigDecimal("15.00"), "USD",
                "MOBILE_MONEY", "Gogo", "Makorokoto", false);

        assertEquals(pending, intent);
        // Never credits or records at request time — settlement does that on confirm.
        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(contribRepo, never()).save(any());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void psp_donate_is_refused_for_non_campaign_and_closed_requests() {
        var bill = openRequest(new BigDecimal("100.00"), BigDecimal.ZERO);
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(bill));
        assertThrows(BillContributionService.ContributionRejected.class,
                () -> service.pspDonate("tok-1", new BigDecimal("15.00"), null, "CARD", null, null, false));

        var closed = openCampaignRequest();
        closed.setStatus("CLOSED");
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(closed));
        assertThrows(BillContributionService.ContributionRejected.class,
                () -> service.pspDonate("tok-1", new BigDecimal("15.00"), null, "CARD", null, null, false));

        verify(fundingService, never()).requestPurposedDeposit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private DepositIntentEntity settledCrowdfundIntent(String metadata) {
        DepositIntentEntity intent = new DepositIntentEntity();
        intent.setDepositId(UUID.randomUUID());
        intent.setWalletId(escrowWalletId);
        intent.setAmount(new BigDecimal("15.00"));
        intent.setCurrency("USD");
        intent.setPurpose(DepositIntentEntity.PURPOSE_CROWDFUNDING);
        intent.setPurposeRef("tok-1");
        intent.setMetadata(metadata);
        return intent;
    }

    @Test
    void settled_psp_intent_records_the_contribution_once_with_no_second_credit() {
        var request = openCampaignRequest();
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));
        DepositIntentEntity intent = settledCrowdfundIntent(
                "{\"contributorName\":\"Gogo\",\"message\":\"Makorokoto\",\"isAnonymous\":true}");

        BillContributionEntity c = service.recordSettledContribution(intent);

        assertEquals(BillContributionEntity.ORIGIN_PSP, c.getOrigin());
        assertEquals("Gogo", c.getContributorName());
        assertEquals("deposit:" + intent.getDepositId(), c.getIdempotencyKey());
        assertEquals(new BigDecimal("15.00"), request.getRaisedAmount());
        // The confirm path already credited the escrow — recording must NOT credit again.
        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).transfer(any(), any(), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<EventOutboxEntity> cap = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepo).save(cap.capture());
        assertEquals(BillContributionService.EVENT_RECORDED, cap.getValue().getEventType());
        assertTrue(cap.getValue().getPayloadJson().contains("\"isAnonymousHint\":true"));

        // Replay (e.g. re-imported statement): row exists under 'deposit:'+id → returned, no re-advance.
        when(contribRepo.findByIdempotencyKey("deposit:" + intent.getDepositId()))
                .thenReturn(Optional.of(c));
        BillContributionEntity replay = service.recordSettledContribution(intent);
        assertEquals(c, replay);
        assertEquals(new BigDecimal("15.00"), request.getRaisedAmount());
        verify(outboxRepo, times(1)).save(any());
    }

    @Test
    void partial_release_transfers_the_requested_amount_to_the_beneficiary() {
        var request = openCampaignRequest();
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));
        when(walletService.getBalance(escrowWalletId)).thenReturn(new BigDecimal("200.00"));

        service.release(request.getId(), new BigDecimal("80.00"), "rel-1", "case-officer");

        verify(walletService).transfer(eq(escrowWalletId), eq(beneficiaryWalletId),
                eq(new BigDecimal("80.00")), eq("tok-1"), any(), eq("SOCIAL_FUNDING"), any(), eq("rel-1"));
        ArgumentCaptor<EventOutboxEntity> cap = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepo).save(cap.capture());
        assertEquals(BillContributionService.EVENT_RELEASED, cap.getValue().getEventType());
        assertTrue(cap.getValue().getPayloadJson().contains("\"amount\":\"80.00\""));
    }

    @Test
    void full_release_with_no_amount_transfers_the_whole_escrow_balance() {
        var request = openCampaignRequest();
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));
        when(walletService.getBalance(escrowWalletId)).thenReturn(new BigDecimal("135.50"));

        service.release(request.getId(), null, null, null);

        verify(walletService).transfer(eq(escrowWalletId), eq(beneficiaryWalletId),
                eq(new BigDecimal("135.50")), eq("tok-1"), any(), eq("SOCIAL_FUNDING"), any(), any());
    }

    @Test
    void over_release_beyond_escrow_balance_is_rejected() {
        var request = openCampaignRequest();
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));
        when(walletService.getBalance(escrowWalletId)).thenReturn(new BigDecimal("50.00"));

        assertThrows(BillContributionService.ContributionRejected.class,
                () -> service.release(request.getId(), new BigDecimal("50.01"), null, null));
        verify(walletService, never()).transfer(any(), any(), any(), any(), any(), any(), any(), any());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void release_is_refused_for_non_campaign_and_cancelled_requests() {
        var bill = openRequest(new BigDecimal("100.00"), BigDecimal.ZERO);
        bill.setTenantId(UUID.randomUUID());
        when(requestRepo.findById(bill.getId())).thenReturn(Optional.of(bill));
        assertThrows(BillContributionService.ContributionRejected.class,
                () -> service.release(bill.getId(), null, null, null));

        var cancelled = openCampaignRequest();
        cancelled.setStatus("CANCELLED_REFUNDING");
        when(requestRepo.findById(cancelled.getId())).thenReturn(Optional.of(cancelled));
        assertThrows(BillContributionService.ContributionRejected.class,
                () -> service.release(cancelled.getId(), null, null, null));

        verify(walletService, never()).transfer(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void every_contribution_emits_a_recorded_outbox_event() {
        var request = openCampaignRequest();
        when(requestRepo.findByShareToken("tok-1")).thenReturn(Optional.of(request));

        service.contribute("tok-1", new BigDecimal("25.00"),
                "cpid-9", "Donor", null, "idem-evt", donorWalletId, null, true);

        ArgumentCaptor<EventOutboxEntity> cap = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepo, times(1)).save(cap.capture());
        EventOutboxEntity evt = cap.getValue();
        assertEquals(BillContributionService.EVENT_RECORDED, evt.getEventType());
        assertEquals(campaignRef.toString(), evt.getSubjectId());
        String payload = evt.getPayloadJson();
        assertTrue(payload.contains("\"requestId\":\"" + request.getId()));
        assertTrue(payload.contains("\"campaignRef\":\"" + campaignRef));
        assertTrue(payload.contains("\"amount\":\"25.00\""));
        assertTrue(payload.contains("\"isAnonymousHint\":true"));
        assertTrue(payload.contains("\"idempotencyKey\":\"idem-evt\""));
        // NPE hazard guard: no null values may enter the payload map (Map.copyOf downstream).
        assertTrue(!payload.contains(":null"));
    }
}
