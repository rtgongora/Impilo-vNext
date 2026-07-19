package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCredentialEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCredentialRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * FJ QR / D-L7: the QR carries the verification code only (no internal ids);
 * signed scans resolve live registry facts; forged, revoked and closed-facility
 * scans collapse to ONE NOT_RECOGNISED shape; provisional facilities cannot be
 * credentialed; signing survives restart.
 */
@ExtendWith(MockitoExtension.class)
class FacilityCredentialServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityCredentialRepository credentialRepository;

    private FacilityCredentialService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityUuid = UUID.randomUUID();
    private FacilityEntity facility;
    private FacilityCredentialEntity stored;

    @BeforeEach
    void setUp() {
        service = new FacilityCredentialService(facilityRepository, credentialRepository, "unit-test-seed");
        TrustContextHolder.set(new TrustContext(
                tenantId, "registrar-1", "REGISTRY_ADMIN", "REGISTRY_ADMIN", null,
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        facility = new FacilityEntity();
        facility.setFacilityUuid(facilityUuid);
        facility.setTenantId(tenantId);
        facility.setName("Bangure Clinic");
        facility.setFacilityCode("ZW010125");
        facility.setStatus("ACTIVE");
        lenient().when(facilityRepository.findByFacilityUuid(facilityUuid))
                .thenReturn(Optional.of(facility));
        lenient().when(credentialRepository.save(any())).thenAnswer(inv -> {
            stored = inv.getArgument(0);
            if (stored.getCredentialUuid() == null) {
                stored.setCredentialUuid(UUID.randomUUID());
            }
            return stored;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void qrCarriesVerificationCodeOnly() {
        FacilityCredentialService.IssuedCredential issued = service.issue(facilityUuid);
        assertThat(issued.qrPayload()).doesNotContain(facilityUuid.toString());
        assertThat(issued.verificationCode()).startsWith("FVC");
    }

    @Test
    void signedScanReturnsPolicyFilteredFacts() {
        FacilityCredentialService.IssuedCredential issued = service.issue(facilityUuid);
        when(credentialRepository.findByVerificationCode(issued.verificationCode()))
                .thenReturn(Optional.of(stored));

        FacilityCredentialService.CredentialVerification result = service.verifyScan(issued.qrPayload());

        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.facilityName()).isEqualTo("Bangure Clinic");
        assertThat(result.facilityCode()).isEqualTo("ZW010125");
    }

    @Test
    void forgedAndRevokedCollapseToOneShape() {
        FacilityCredentialService.IssuedCredential issued = service.issue(facilityUuid);

        // Forged: a tampered signature over the same payload fails verification.
        String[] parts = issued.qrPayload().split("\\.", 2);
        String tampered = parts[0] + ".bm90LWEtdmFsaWQtc2lnbmF0dXJl";
        FacilityCredentialService.CredentialVerification forged = service.verifyScan(tampered);

        // Revoked code (valid signature, but the DB row is no longer ACTIVE).
        stored.setStatus(FacilityCredentialEntity.STATUS_REVOKED);
        when(credentialRepository.findByVerificationCode(issued.verificationCode()))
                .thenReturn(Optional.of(stored));
        FacilityCredentialService.CredentialVerification revoked = service.verifyScan(issued.qrPayload());

        assertThat(forged.status()).isEqualTo("NOT_RECOGNISED");
        assertThat(revoked).isEqualTo(forged);
    }

    @Test
    void provisionalFacilityCannotBeCredentialed() {
        facility.setStatus(FacilityRegistrationService.STATUS_PROVISIONAL);
        assertThatThrownBy(() -> service.issue(facilityUuid))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void signingSurvivesRestart() {
        FacilityCredentialService.IssuedCredential issued = service.issue(facilityUuid);
        FacilityCredentialService restarted = new FacilityCredentialService(
                facilityRepository, credentialRepository, "unit-test-seed");
        when(credentialRepository.findByVerificationCode(issued.verificationCode()))
                .thenReturn(Optional.of(stored));
        assertThat(restarted.verifyScan(issued.qrPayload()).status()).isEqualTo("VERIFIED");
    }
}
