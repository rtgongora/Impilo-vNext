package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sweep is the only code that sets LICENCE_DUE_FOR_RENEWAL / LAPSED. It runs outside a request
 * (no TrustContext) and writes lifecycle directly + emits an outbox notice, idempotently.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LicenceRenewalSweepTest {

    @Mock private LicenseRepository licenseRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private LicenceRenewalSweep sweep() {
        return new LicenceRenewalSweep(licenseRepository, providerRepository, outboxRepository);
    }

    private LicenseEntity licence(String providerLifecycle, LocalDate validTo) {
        ProviderEntity provider = new ProviderEntity();
        provider.setProviderPublicId("PRV-1");
        provider.setLifecycleStatus(providerLifecycle);
        provider.setVersion(1);
        LicenseEntity l = new LicenseEntity();
        l.setProvider(provider);
        l.setStatus("ACTIVE");
        l.setValidTo(validTo);
        return l;
    }

    @Test
    void markDueForRenewal_movesLicencedActiveToDue() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(60);
        LicenseEntity l = licence("LICENCED_ACTIVE", from.plusDays(30));
        when(licenseRepository.findByStatusAndValidToBetween("ACTIVE", from, to)).thenReturn(List.of(l));

        int moved = sweep().markDueForRenewal(from, to);

        assertEquals(1, moved);
        assertEquals("LICENCE_DUE_FOR_RENEWAL", l.getProvider().getLifecycleStatus());
        verify(providerRepository).save(l.getProvider());
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void markDueForRenewal_isIdempotent_whenAlreadyDue() {
        LocalDate from = LocalDate.now();
        LicenseEntity l = licence("LICENCE_DUE_FOR_RENEWAL", from.plusDays(10));
        when(licenseRepository.findByStatusAndValidToBetween(eq("ACTIVE"), any(), any())).thenReturn(List.of(l));

        int moved = sweep().markDueForRenewal(from, from.plusDays(60));

        assertEquals(0, moved);
        verify(providerRepository, never()).save(any());
    }

    @Test
    void markLapsed_movesExpiredActiveToLapsed() {
        LocalDate today = LocalDate.now();
        LicenseEntity l = licence("LICENCE_DUE_FOR_RENEWAL", today.minusDays(1));
        when(licenseRepository.findByStatusAndValidToBefore("ACTIVE", today)).thenReturn(List.of(l));

        int moved = sweep().markLapsed(today);

        assertEquals(1, moved);
        assertEquals("LAPSED", l.getProvider().getLifecycleStatus());
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }
}
