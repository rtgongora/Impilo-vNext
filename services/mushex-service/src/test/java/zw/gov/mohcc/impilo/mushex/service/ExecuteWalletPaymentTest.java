package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.integration.CredentialVerificationClient;
import zw.gov.mohcc.impilo.mushex.integration.MusheWalletAdapter;
import zw.gov.mohcc.impilo.mushex.integration.ProviderContractClient;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterResponse;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter;
import zw.gov.mohcc.impilo.mushex.service.rail.DefaultRailSelectionPolicy;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The pay-confirm seam's execution leg: executeWalletPayment debits the
 * CPID-keyed Mushe wallet for the OUTSTANDING amount and records the payment
 * so the intent reaches PAID from a real debit — no sandbox auto-settle.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecuteWalletPaymentTest {

    @Mock private PaymentIntentRepository intentRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ReceiptService receiptService;
    @Mock private ProviderContractClient providerContractClient;
    @Mock private MusheWalletAdapter walletAdapter;

    private static final CredentialVerificationClient NOOP_CREDENTIALS =
            (tenantId, providerId) -> new CredentialVerificationClient.CredentialVerificationResult(
                    true, "VALID", "test-verification-ref", null);

    private PaymentIntentService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MushexProperties props = new MushexProperties();
        service = new PaymentIntentService(intentRepository, outboxRepository, receiptService,
                new ObjectMapper(), NOOP_CREDENTIALS, providerContractClient, walletAdapter, props,
                new DefaultRailSelectionPolicy(registry(), props));
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "PATIENT", "PAYMENT",
                "device-1", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        when(intentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletAdapter.payFromWallet(any(), any(), any(), any(), any()))
                .thenReturn(new MusheWalletAdapter.WalletPaymentResult("TXN1", "COMPLETED", new BigDecimal("25.00")));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private static AdapterRegistry registry() {
        return new AdapterRegistry(List.of(new PaymentRailAdapter() {
            @Override public String adapterType() { return AdapterType.SANDBOX.name(); }
            @Override public AdapterResponse initiatePayment(String i, BigDecimal a, String c, Map<String, String> cfg) {
                return new AdapterResponse("ref", "SUCCESS", "", Map.of());
            }
            @Override public AdapterResponse checkStatus(String r, Map<String, String> cfg) {
                return new AdapterResponse("ref", "SUCCESS", "", Map.of());
            }
            @Override public boolean verifyWebhook(String s, String p, Map<String, String> cfg) { return true; }
            @Override public AdapterResponse initiateRefund(String r, BigDecimal a, Map<String, String> cfg) {
                return new AdapterResponse("ref", "SUCCESS", "", Map.of());
            }
        }));
    }

    private PaymentIntentEntity intent(IntentStatus status, String metadata) {
        PaymentIntentEntity i = new PaymentIntentEntity();
        i.setIntentId("01INTENT");
        i.setTenantId(tenantId);
        i.setSourceType(SourceType.MSIKA_ORDER);
        i.setSourceId("ORDER1");
        i.setCurrency("USD");
        i.setAmountTotal(new BigDecimal("25.00"));
        i.setAmountPaid(BigDecimal.ZERO);
        i.setStatus(status);
        i.setMetadata(metadata);
        when(intentRepository.findById("01INTENT")).thenReturn(Optional.of(i));
        return i;
    }

    @Test
    void debitsCpidWalletForOutstanding_andIntentReachesPaid() {
        // provider_id mirrors what msika-flow's buildIntentMetadata always sends (the payee).
        intent(IntentStatus.CREATED,
                "{\"patient_cpid\":\"CPID-ZW-1\",\"order_id\":\"ORDER1\",\"provider_id\":\"vendor:V1\"}");

        PaymentIntentEntity result = service.executeWalletPayment("01INTENT");

        verify(walletAdapter).payFromWallet(eq(tenantId), eq("CPID-ZW-1"),
                eq(new BigDecimal("25.00")), eq("01INTENT"), any());
        assertEquals(IntentStatus.PAID, result.getStatus());
        assertEquals(new BigDecimal("25.00"), result.getAmountPaid());
    }

    @Test
    void refusesWithoutPatientCpid_walletNeverTouched() {
        intent(IntentStatus.CREATED, "{\"order_id\":\"ORDER1\"}");

        assertThrows(IllegalStateException.class, () -> service.executeWalletPayment("01INTENT"));
        verify(walletAdapter, never()).payFromWallet(any(), any(), any(), any(), any());
    }

    @Test
    void refusesAlreadyPaidIntent_walletNeverDebitedTwice() {
        PaymentIntentEntity i = intent(IntentStatus.PAID, "{\"patient_cpid\":\"CPID-ZW-1\"}");
        i.setAmountPaid(new BigDecimal("25.00"));

        assertThrows(IllegalStateException.class, () -> service.executeWalletPayment("01INTENT"));
        verify(walletAdapter, never()).payFromWallet(any(), any(), any(), any(), any());
    }
}
