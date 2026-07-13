package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies the certificate-download status gate: a suspended/revoked/expired licence must never
 * stream a PDF (a printed cert must reflect current standing), while an active licence downloads.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LicenseServiceDownloadTest {

    @Mock private LicenseRepository licenseRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private LicenseCertificatePdfGenerator pdfGenerator;

    private LicenseService service() {
        return new LicenseService(licenseRepository, providerRepository, outboxRepository, pdfGenerator);
    }

    private LicenseEntity licence(String status, LocalDate validTo) {
        ProviderEntity provider = new ProviderEntity();
        provider.setProviderPublicId("PRV-1");
        LicenseEntity l = new LicenseEntity();
        l.setProvider(provider);
        l.setStatus(status);
        l.setValidTo(validTo);
        return l;
    }

    @org.junit.jupiter.api.BeforeEach
    void ctx() {
        TrustContextHolder.set(new TrustContext(UUID.randomUUID(), "actor-1", "PROVIDER", "REGISTRY",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clear() {
        TrustContextHolder.clear();
    }

    @Test
    void download_rejectsSuspendedLicence() {
        when(licenseRepository.findById(9L)).thenReturn(Optional.of(licence("SUSPENDED", LocalDate.now().plusYears(1))));
        assertThrows(IllegalStateException.class, () -> service().downloadCertificate("PRV-1", 9L));
    }

    @Test
    void download_rejectsExpiredLicence() {
        when(licenseRepository.findById(9L)).thenReturn(Optional.of(licence("ACTIVE", LocalDate.now().minusDays(1))));
        assertThrows(IllegalStateException.class, () -> service().downloadCertificate("PRV-1", 9L));
    }

    @Test
    void download_streamsActiveLicence() {
        when(licenseRepository.findById(9L)).thenReturn(Optional.of(licence("ACTIVE", LocalDate.now().plusYears(1))));
        when(pdfGenerator.generate(any())).thenReturn(new byte[] { 1, 2, 3 });
        assertArrayEquals(new byte[] { 1, 2, 3 }, service().downloadCertificate("PRV-1", 9L));
    }
}
