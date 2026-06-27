package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G018: certificate download must return a real PDF, not 500 with
 * UnsupportedOperationException.
 */
class LicenseCertificatePdfGeneratorTest {

    private final LicenseCertificatePdfGenerator generator = new LicenseCertificatePdfGenerator();

    @Test
    void generatesARealPdf() {
        ProviderEntity provider = new ProviderEntity();
        provider.setProviderPublicId("PRV-001");
        provider.setTitle("Dr");
        provider.setGivenName("Tariro");
        provider.setFamilyName("Moyo");

        CouncilEntity council = new CouncilEntity();
        council.setName("Medical and Dental Practitioners Council");

        LicenseEntity license = new LicenseEntity();
        license.setProvider(provider);
        license.setCouncil(council);
        license.setLicenseType("MEDICAL_PRACTITIONER");
        license.setLicenseNumber("MP-2026-001");
        license.setStatus("ACTIVE");
        license.setValidFrom(LocalDate.of(2026, 1, 1));
        license.setValidTo(LocalDate.of(2026, 12, 31));

        byte[] pdf = generator.generate(license);

        assertThat(pdf).isNotEmpty();
        // PDF magic header
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void toleratesMissingOptionalFields() {
        ProviderEntity provider = new ProviderEntity();
        provider.setProviderPublicId("PRV-002");

        LicenseEntity license = new LicenseEntity();
        license.setProvider(provider);
        license.setLicenseType("NURSE");
        license.setStatus("ACTIVE");

        byte[] pdf = generator.generate(license);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
