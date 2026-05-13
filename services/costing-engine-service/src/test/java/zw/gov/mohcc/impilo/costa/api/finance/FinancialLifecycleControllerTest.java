package zw.gov.mohcc.impilo.costa.api.finance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.CostaPaymentHandoffEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.InvoiceEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.PaymentStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.BillHeaderRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargeRecordRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.CostaPaymentHandoffRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceLineRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.PaymentAllocationRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.PaymentRepository;
import zw.gov.mohcc.impilo.costa.service.ChargeRecordService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialLifecycleControllerTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private PaymentAllocationRepository paymentAllocationRepository;
    @Mock private ChargeRecordRepository chargeRecordRepository;
    @Mock private BillHeaderRepository billHeaderRepository;
    @Mock private CostaPaymentHandoffRepository costaPaymentHandoffRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ChargeRecordService chargeRecordService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    private FinancialLifecycleController controller;

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
                "device-1", correlationId, facilityId, null, null, AccessMode.INTERNAL));
        controller = new FinancialLifecycleController(
                invoiceRepository, invoiceLineRepository, paymentAllocationRepository,
                chargeRecordRepository, billHeaderRepository, costaPaymentHandoffRepository,
                paymentRepository, chargeRecordService);
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void listInvoicesByEncounter_mergesInvoiceWithIntentAndPaymentSignals() {
        BillHeaderEntity bill = new BillHeaderEntity();
        bill.setBillId("BILL-1");
        bill.setEncounterId("ENC-1");
        bill.setTenantId(tenantId);

        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setInvoiceId("INV-1");
        invoice.setBillId("BILL-1");
        invoice.setInvoiceNumber("INV-001");
        invoice.setInvoiceStatus("ISSUED");
        invoice.setSubtotal(new BigDecimal("10.00"));
        invoice.setTotal(new BigDecimal("10.00"));
        invoice.setCurrency("USD");
        invoice.setTenantId(tenantId);

        CostaPaymentHandoffEntity handoff = new CostaPaymentHandoffEntity();
        handoff.setTenantId(tenantId);
        handoff.setInvoiceId("INV-1");
        handoff.setMushexIntentId("PI-1");
        handoff.setStatus("CREATED");

        PaymentEntity payment = new PaymentEntity();
        payment.setBillId("BILL-1");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("10.00"));
        payment.setCurrency("USD");
        payment.setPaidAt(OffsetDateTime.parse("2026-05-13T11:00:00Z"));

        when(billHeaderRepository.findByEncounterId("ENC-1")).thenReturn(List.of(bill));
        when(invoiceRepository.findByBillId("BILL-1")).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findById("INV-1")).thenReturn(Optional.of(invoice));
        when(costaPaymentHandoffRepository.findByTenantIdAndInvoiceIdIn(tenantId, List.of("INV-1")))
                .thenReturn(List.of(handoff));
        when(paymentRepository.findByBillId("BILL-1")).thenReturn(List.of(payment));

        var response = controller.listInvoicesByEncounter("ENC-1");

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        List<?> rows = (List<?>) response.getBody().data();
        assertEquals(1, rows.size());
        @SuppressWarnings("unchecked")
        var row = (java.util.Map<String, Object>) rows.get(0);
        assertEquals("INV-1", row.get("invoice_id"));
        assertEquals("PI-1", row.get("mushex_intent_id"));
        assertEquals("PENDING", String.valueOf(row.get("payment_status")));
    }
}

