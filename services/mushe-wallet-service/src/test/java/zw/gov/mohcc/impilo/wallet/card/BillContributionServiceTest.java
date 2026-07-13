package zw.gov.mohcc.impilo.wallet.card;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.wallet.core.WalletService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.BillContributionRequestEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.BillContributionRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.BillContributionRequestRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillContributionServiceTest {

    private BillContributionRequestRepository requestRepo;
    private BillContributionRepository contribRepo;
    private WalletService walletService;
    private BillContributionService service;
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        requestRepo = mock(BillContributionRequestRepository.class);
        contribRepo = mock(BillContributionRepository.class);
        walletService = mock(WalletService.class);
        service = new BillContributionService(requestRepo, contribRepo, walletService);
        when(requestRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(contribRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(contribRepo.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(walletService.credit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
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

    @Test
    void create_generates_a_share_token() {
        var r = service.createRequest(UUID.randomUUID(), walletId, "Bill", "BILL-9",
                new BigDecimal("100.00"), "USD", "cpid-1");
        org.junit.jupiter.api.Assertions.assertNotNull(r.getShareToken());
        assertEquals("OPEN", r.getStatus());
    }

    @Test
    void contribute_credits_the_beneficiary_wallet_and_advances_the_total() {
        var request = openRequest(new BigDecimal("100.00"), BigDecimal.ZERO);
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
    void contribution_that_meets_target_fulfils_the_request() {
        var request = openRequest(new BigDecimal("100.00"), new BigDecimal("80.00"));
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
}
