package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.integration.ShareSlipClient;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiagnosticShareServiceTest {

    @Mock private OrderStateMachine stateMachine;
    @Mock private ShareSlipClient shareSlipClient;
    @Mock private EventOutboxRepository outboxRepository;

    private DiagnosticShareService service;
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String ORDER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    @BeforeEach
    void setUp() {
        ObjectMapper om = OrosTestObjectMapper.create();
        service = new DiagnosticShareService(stateMachine, shareSlipClient, outboxRepository, om);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "intake-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
    }

    private OrderEntity order() {
        OrderEntity o = new OrderEntity();
        o.setOrderId(ORDER_ID);
        o.setTenantId(TENANT_ID);
        o.setAccessionNumber("ACC-2026-AB-1");
        return o;
    }

    @Test
    @DisplayName("printable issues a document-less DIAGNOSTIC_ORDER link and emits an event")
    void printable() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(stateMachine.getOrder(ORDER_ID)).thenReturn(order());
            when(shareSlipClient.createLink(eq("DIAGNOSTIC_ORDER"), eq(ORDER_ID), any(),
                    eq(List.of()), any(), eq(168), eq(1)))
                    .thenReturn(new ShareSlipClient.ShareLinkRef("link-1", "tok-1", "ACTIVE", null));

            ShareSlipClient.ShareLinkRef ref = service.printable(ORDER_ID, 168, 1);

            assertThat(ref.shareToken()).isEqualTo("tok-1");
            verify(outboxRepository).save(any(EventOutboxEntity.class));
        }
    }

    @Test
    @DisplayName("claim of a valid DIAGNOSTIC_ORDER token resolves and returns the order")
    void claimSuccess() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(shareSlipClient.claim("tok-1", "123456", "dev-1"))
                    .thenReturn(new ShareSlipClient.ClaimRef(true, "DIAGNOSTIC_ORDER", ORDER_ID, "ok"));
            when(stateMachine.getOrder(ORDER_ID)).thenReturn(order());

            OrderEntity result = service.claim("tok-1", "123456", "dev-1");

            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
            verify(outboxRepository).save(any(EventOutboxEntity.class));
        }
    }

    @Test
    @DisplayName("a failed claim is rejected")
    void claimFailure() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(shareSlipClient.claim(any(), any(), any()))
                    .thenReturn(new ShareSlipClient.ClaimRef(false, null, null, "Invalid OTP"));

            assertThatThrownBy(() -> service.claim("tok-1", "000000", "dev-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("QR claim failed");
        }
    }

    @Test
    @DisplayName("a token for a non-order subject is rejected")
    void claimWrongSubject() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            when(shareSlipClient.claim(any(), any(), any()))
                    .thenReturn(new ShareSlipClient.ClaimRef(true, "DIAGNOSTIC_RESULT", "r-1", "ok"));

            assertThatThrownBy(() -> service.claim("tok-1", "123456", "dev-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not reference a diagnostic order");
        }
    }
}
