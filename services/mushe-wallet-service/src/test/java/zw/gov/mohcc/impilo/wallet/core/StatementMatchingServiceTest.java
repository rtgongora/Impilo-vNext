package zw.gov.mohcc.impilo.wallet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.wallet.persistence.entity.DepositIntentEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.DepositIntentRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bank→wallet matching: a ZIPIT line quoting a deposit reference confirms and
 * credits; anything without a matching pending intent or with a mismatched
 * amount is UNMATCHED, never force-credited.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatementMatchingServiceTest {

    @Mock DepositIntentRepository depositRepo;
    @Mock FundingService fundingService;

    private StatementMatchingService service() {
        return new StatementMatchingService(depositRepo, fundingService);
    }

    private DepositIntentEntity pending(String code, String amount) {
        DepositIntentEntity d = new DepositIntentEntity();
        d.setDepositId(UUID.randomUUID());
        d.setReferenceCode(code);
        d.setAmount(new BigDecimal(amount));
        d.setStatus(DepositIntentEntity.STATUS_PENDING);
        return d;
    }

    @Test
    void referenceExtraction_findsCodeAnywhereInNarrative() {
        assertEquals("IMP-ABCD2345",
                StatementMatchingService.extractReference("ZIPIT FRM J MOYO REF IMP-ABCD2345 THANKS"));
        assertNull(StatementMatchingService.extractReference("random deposit no code"));
        assertNull(StatementMatchingService.extractReference(null));
    }

    @Test
    void matchingLine_confirmsAndCredits() {
        when(depositRepo.findByReferenceCode("IMP-ABCD2345"))
                .thenReturn(Optional.of(pending("IMP-ABCD2345", "50.00")));

        var outcomes = service().matchStatement(List.of(
                new StatementMatchingService.StatementLine(
                        "ZIPIT REF IMP-ABCD2345", new BigDecimal("50.00"), "BANK-TX-1")));

        assertEquals("MATCHED", outcomes.get(0).result());
        verify(fundingService).confirmDepositByReference(eq("IMP-ABCD2345"), eq("BANK-TX-1"), any());
    }

    @Test
    void amountMismatch_isUnmatched_neverCredited() {
        when(depositRepo.findByReferenceCode("IMP-ABCD2345"))
                .thenReturn(Optional.of(pending("IMP-ABCD2345", "50.00")));

        var outcomes = service().matchStatement(List.of(
                new StatementMatchingService.StatementLine(
                        "ZIPIT REF IMP-ABCD2345", new BigDecimal("49.99"), "BANK-TX-2")));

        assertEquals("UNMATCHED", outcomes.get(0).result());
        assertTrue(outcomes.get(0).detail().contains("amount mismatch"));
        verify(fundingService, never()).confirmDepositByReference(any(), any(), any());
    }

    @Test
    void noReferenceInNarrative_isUnmatched() {
        var outcomes = service().matchStatement(List.of(
                new StatementMatchingService.StatementLine(
                        "cash deposit no reference", new BigDecimal("50.00"), "BANK-TX-3")));
        assertEquals("UNMATCHED", outcomes.get(0).result());
        verify(fundingService, never()).confirmDepositByReference(any(), any(), any());
    }

    @Test
    void referenceWithNoIntent_isUnmatched() {
        when(depositRepo.findByReferenceCode("IMP-ZZZZ2345")).thenReturn(Optional.empty());
        var outcomes = service().matchStatement(List.of(
                new StatementMatchingService.StatementLine(
                        "ZIPIT REF IMP-ZZZZ2345", new BigDecimal("50.00"), "BANK-TX-4")));
        assertEquals("UNMATCHED", outcomes.get(0).result());
        verify(fundingService, never()).confirmDepositByReference(any(), any(), any());
    }

    @Test
    void alreadyConfirmedIntent_isUnmatched_idempotentReimportSafe() {
        DepositIntentEntity confirmed = pending("IMP-ABCD2345", "50.00");
        confirmed.setStatus(DepositIntentEntity.STATUS_CONFIRMED);
        when(depositRepo.findByReferenceCode("IMP-ABCD2345")).thenReturn(Optional.of(confirmed));

        var outcomes = service().matchStatement(List.of(
                new StatementMatchingService.StatementLine(
                        "ZIPIT REF IMP-ABCD2345", new BigDecimal("50.00"), "BANK-TX-5")));

        assertEquals("UNMATCHED", outcomes.get(0).result());
        assertTrue(outcomes.get(0).detail().contains("not pending"));
        verify(fundingService, never()).confirmDepositByReference(any(), any(), any());
    }
}
