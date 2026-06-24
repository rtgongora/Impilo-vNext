package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.RefundEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.RefundRepository;
import zw.gov.mohcc.impilo.mushex.service.LedgerService;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.RefundService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the E2 fix (G016): a large refund is BLOCKED (not merely logged) unless step-up is
 * present. Before the fix the service logged a warning and proceeded to write the refund +
 * ledger entry.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundServiceTest {

    @Mock private RefundRepository refundRepository;
    @Mock private PaymentIntentRepository intentRepository;
    @Mock private PaymentIntentService intentService;
    @Mock private LedgerService ledgerService;
    @Mock private EventOutboxRepository outboxRepository;

    private RefundService service;
    private static final String INTENT_ID = "intent-1";

    @BeforeEach
    void setUp() {
        service = new RefundService(refundRepository, intentRepository, intentService, ledgerService,
                outboxRepository, new MushexProperties(), new ObjectMapper());

        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId(INTENT_ID);
        intent.setStatus(IntentStatus.PAID);
        intent.setAmountPaid(new BigDecimal("5000.00"));
        intent.setCurrency("USD");
        when(intentRepository.findById(INTENT_ID)).thenReturn(Optional.of(intent));
        when(refundRepository.findByIntentId(INTENT_ID)).thenReturn(List.of());
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    private void context(AccessMode mode) {
        TrustContextHolder.set(new TrustContext(UUID.randomUUID(), "actor-1", "FACILITY_FINANCE",
                "BILLING", "device-1", UUID.randomUUID(), UUID.randomUUID(), null, null, mode));
    }

    private void requestWithStepUpToken(boolean present) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (present) {
            request.addHeader("X-Step-Up-Token", "valid-step-up-token");
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void largeRefund_external_noStepUp_isBlocked_andWritesNothing() {
        context(AccessMode.EXTERNAL);
        requestWithStepUpToken(false);

        assertThatThrownBy(() -> service.requestRefund(INTENT_ID, new BigDecimal("2000.00"), "overpayment"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("STEP_UP_REQUIRED");

        verify(refundRepository, never()).save(any());
        verify(ledgerService, never()).postRefund(any(), any(), any(), any());
    }

    @Test
    void largeRefund_external_withStepUp_proceeds() {
        context(AccessMode.EXTERNAL);
        requestWithStepUpToken(true);

        RefundEntity refund = service.requestRefund(INTENT_ID, new BigDecimal("2000.00"), "overpayment");

        assertThat(refund).isNotNull();
        verify(refundRepository).save(any());
        verify(ledgerService).postRefund(any(), any(), any(), any());
    }

    @Test
    void largeRefund_internalServiceCall_bypassesStepUp() {
        context(AccessMode.INTERNAL); // trusted service-to-service, no token
        RefundEntity refund = service.requestRefund(INTENT_ID, new BigDecimal("2000.00"), "auto-reversal");
        assertThat(refund).isNotNull();
        verify(refundRepository).save(any());
    }

    @Test
    void smallRefund_doesNotRequireStepUp() {
        context(AccessMode.EXTERNAL);
        requestWithStepUpToken(false);
        RefundEntity refund = service.requestRefund(INTENT_ID, new BigDecimal("500.00"), "partial");
        assertThat(refund).isNotNull();
        verify(refundRepository).save(any());
    }
}
