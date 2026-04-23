package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.PaymentStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.BillHeaderRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BillLineRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.ClaimPackRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EncounterRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceLineRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.PaymentRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.RefundRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentIntegrationServiceTest {

    @Mock BillHeaderRepository billHeaderRepository;
    @Mock EncounterRepository encounterRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock RefundRepository refundRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock ClaimPackRepository claimPackRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock ReceivablesService receivablesService;
    @Mock PatientAccountService patientAccountService;
    @Mock BillLineRepository billLineRepository;
    @Mock InvoiceLineRepository invoiceLineRepository;
    @Mock PaymentAllocationService paymentAllocationService;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    PaymentIntegrationService paymentIntegrationService;

    @BeforeEach
    void stubObjectMapper() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void mapMushexIntentStatus_coversCoreStates() {
        assertEquals(Optional.of(PaymentStatus.PAID), PaymentIntegrationService.mapMushexIntentStatus("PAID"));
        assertEquals(Optional.of(PaymentStatus.FAILED), PaymentIntegrationService.mapMushexIntentStatus("FAILED"));
        assertTrue(PaymentIntegrationService.mapMushexIntentStatus("REFUND_PENDING").isEmpty());
    }

    @Test
    void handlePaymentStatusUpdate_appliesReceivableOnceOnFirstPaid() {
        String intent = "01HZTESTMUSHEXINTENT";
        PaymentEntity payment = new PaymentEntity();
        payment.setId(99L);
        payment.setBillId("01HZBILLTEST0001");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("50.00"));
        payment.setPaidAmount(BigDecimal.ZERO);
        payment.setCurrency("USD");

        BillHeaderEntity bill = new BillHeaderEntity();
        bill.setBillId(payment.getBillId());
        bill.setTenantId(UUID.randomUUID());

        when(paymentRepository.findByMushexPaymentIntentId(intent)).thenReturn(Optional.of(payment));
        when(billHeaderRepository.findById(payment.getBillId())).thenReturn(Optional.of(bill));

        paymentIntegrationService.handlePaymentStatusUpdate(intent, "PAID", new BigDecimal("50.00"));
        paymentIntegrationService.handlePaymentStatusUpdate(intent, "PAID", new BigDecimal("50.00"));

        verify(receivablesService, times(1)).applyPaymentToBill(bill, new BigDecimal("50.00"));
        verify(paymentAllocationService, times(1)).recordFromMushexCapture(
                payment, bill, new BigDecimal("50.00"), intent);
        assertEquals(PaymentStatus.PAID, payment.getStatus());
    }
}
