package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.mushex.domain.entity.CardProfileEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceRequestEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ReversalRecordEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.WalletAccountEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.CardProfileRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.RemittanceRequestRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ReversalRecordRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.WalletAccountRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.WalletTransactionRepository;
import zw.gov.mohcc.impilo.mushex.service.FinancialCardReversalPlatformService;
import zw.gov.mohcc.impilo.mushex.service.PlatformResourceNotFoundException;
import zw.gov.mohcc.impilo.mushex.service.RemittanceTransferService;
import zw.gov.mohcc.impilo.mushex.service.WalletPlatformService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformReadDetailServiceTest {

    @Mock private WalletAccountRepository walletAccountRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private CardProfileRepository cardProfileRepository;
    @Mock private ReversalRecordRepository reversalRecordRepository;
    @Mock private RemittanceRequestRepository remittanceRequestRepository;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID otherTenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
                "device-1", correlationId, facilityId, null, null, AccessMode.INTERNAL
        ));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void walletGetById_returnsRowForCurrentTenant() {
        WalletAccountEntity row = new WalletAccountEntity();
        row.setWalletId("WLT-1");
        row.setTenantId(tenantId);
        when(walletAccountRepository.findById("WLT-1")).thenReturn(Optional.of(row));

        WalletPlatformService service = new WalletPlatformService(
                walletAccountRepository, walletTransactionRepository, outboxRepository, new ObjectMapper());
        WalletAccountEntity got = service.getWallet("WLT-1");

        assertEquals("WLT-1", got.getWalletId());
    }

    @Test
    void cardGetById_rejectsCrossTenantRecord() {
        CardProfileEntity row = new CardProfileEntity();
        row.setCardProfileId("CARD-1");
        row.setTenantId(otherTenantId);
        when(cardProfileRepository.findById("CARD-1")).thenReturn(Optional.of(row));

        FinancialCardReversalPlatformService service = new FinancialCardReversalPlatformService(
                cardProfileRepository, reversalRecordRepository);
        assertThrows(PlatformResourceNotFoundException.class, () -> service.getCardProfile("CARD-1"));
    }

    @Test
    void reversalGetById_notFoundThrowsPlatformNotFound() {
        when(reversalRecordRepository.findById("REV-404")).thenReturn(Optional.empty());

        FinancialCardReversalPlatformService service = new FinancialCardReversalPlatformService(
                cardProfileRepository, reversalRecordRepository);
        assertThrows(PlatformResourceNotFoundException.class, () -> service.getReversal("REV-404"));
    }

    @Test
    void remittanceGetById_rejectsCrossTenantRecord() {
        RemittanceRequestEntity row = new RemittanceRequestEntity();
        row.setRemittanceRequestId("REM-1");
        row.setTenantId(otherTenantId);
        when(remittanceRequestRepository.findById("REM-1")).thenReturn(Optional.of(row));

        RemittanceTransferService service = new RemittanceTransferService(
                remittanceRequestRepository, outboxRepository, new ObjectMapper());
        assertThrows(PlatformResourceNotFoundException.class, () -> service.getById("REM-1"));
    }
}

