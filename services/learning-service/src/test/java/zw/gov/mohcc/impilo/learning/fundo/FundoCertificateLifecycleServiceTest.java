package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.learning.persistence.entity.CertificateEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;

@ExtendWith(MockitoExtension.class)
class FundoCertificateLifecycleServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock private CertificateRepository certificateRepository;
    @Mock private FundoOutboxAppender outbox;
    @Mock private FundoNotificationIntentWriter notifications;

    private FundoCertificateLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new FundoCertificateLifecycleService(
                certificateRepository, outbox, notifications, true, 30L);
    }

    private CertificateEntity cert(String title, OffsetDateTime validUntil) {
        CertificateEntity c = new CertificateEntity();
        c.setId(UUID.randomUUID());
        c.setTenantId(TENANT);
        c.setSubjectType("PROVIDER");
        c.setSubjectId("PRV-1");
        c.setTitle(title);
        c.setStatus("ISSUED");
        c.setValidUntil(validUntil);
        return c;
    }

    @Test
    void expirySweepTransitionsToExpiredAndEmitsEvent() {
        CertificateEntity lapsed = cert("REG-101", OffsetDateTime.now().minusDays(1));
        when(certificateRepository.findByStatusAndValidUntilNotNullAndValidUntilBefore(
                        eq("ISSUED"), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(lapsed));

        int count = service.runExpirySweep();

        assertThat(count).isEqualTo(1);
        assertThat(lapsed.getStatus()).isEqualTo("EXPIRED");
        verify(certificateRepository).save(lapsed);
        verify(outbox).append(eq("certificate"), eq(lapsed.getId().toString()),
                eq(FundoNativeEventTypes.CERTIFICATE_EXPIRED), any());
        verify(notifications).record(eq(TENANT), eq("PROVIDER"), eq("PRV-1"),
                eq("learning.certificate.expired"), any(), any(), any(), any(), any());
    }

    @Test
    void refresherSweepMarksNotifiedAndRecordsIntentOnce() {
        CertificateEntity soon = cert("EHR-101", OffsetDateTime.now().plusDays(10));
        when(certificateRepository.findByStatusAndRefresherNotifiedAtIsNullAndValidUntilNotNullAndValidUntilBetween(
                        eq("ISSUED"), any(OffsetDateTime.class), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(soon));

        int count = service.runRefresherSweep();

        assertThat(count).isEqualTo(1);
        assertThat(soon.getRefresherNotifiedAt()).isNotNull();
        verify(certificateRepository).save(soon);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        verify(outbox).append(eq("certificate"), eq(soon.getId().toString()), eventType.capture(), any());
        assertThat(eventType.getValue()).isEqualTo(FundoNativeEventTypes.CERTIFICATE_REFRESHER_DUE);
        verify(notifications, times(1)).record(any(), any(), any(),
                eq("learning.certificate.refresher.due"), any(), any(), any(), any(), any());
    }

    @Test
    void disabledServiceDoesNothingOnSweep() {
        FundoCertificateLifecycleService disabled = new FundoCertificateLifecycleService(
                certificateRepository, outbox, notifications, false, 30L);
        disabled.sweep();
        verify(certificateRepository, never()).save(any());
        verify(outbox, never()).append(any(), any(), any(), any());
    }
}
