package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBadgeEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderBadgeRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * PJ6/D-P5: the QR carries serial + provider public id only (never HID);
 * signed scans verify against the live registry; a revoked badge, a forged QR
 * and a not-in-standing provider all collapse to ONE NOT_RECOGNISED shape;
 * revocation touches the serial only.
 */
@ExtendWith(MockitoExtension.class)
class ProviderBadgeServiceTest {

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderBadgeRepository badgeRepository;
    @Mock private PublicPractitionerProjectionSupport projectionSupport;

    private ProviderBadgeService service;
    private BadgeSigningService signingService;

    private final UUID tenantId = UUID.randomUUID();
    private ProviderEntity provider;
    private ProviderBadgeEntity storedBadge;

    @BeforeEach
    void setUp() {
        signingService = new BadgeSigningService("unit-test-seed");
        service = new ProviderBadgeService(providerRepository, badgeRepository,
                signingService, projectionSupport, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(
                tenantId, "registrar-1", "OPERATOR", "REGISTRY_ADMIN", null,
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        provider = new ProviderEntity();
        provider.setId(42L);
        provider.setTenantId(tenantId);
        provider.setProviderPublicId("PROVPUB0000000000000000001");
        provider.setGivenName("Jane");
        provider.setFamilyName("Moyo");
        provider.setProfession("NURSE");
        provider.setLifecycleStatus("LICENCED_ACTIVE");
        provider.deriveStatusProjections();

        lenient().when(badgeRepository.save(any())).thenAnswer(inv -> {
            storedBadge = inv.getArgument(0);
            return storedBadge;
        });
        lenient().when(projectionSupport.resolveLicenceWindow(any(), any()))
                .thenReturn(PublicPractitionerProjectionSupport.LicenceWindow.EMPTY);
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private ProviderBadgeService.IssuedBadge issueBadge() {
        when(providerRepository.findByProviderPublicIdAndTenantId(
                provider.getProviderPublicId(), tenantId)).thenReturn(Optional.of(provider));
        return service.issue(provider.getProviderPublicId(), "print-1");
    }

    @Test
    void qrCarriesSerialAndProviderPublicIdOnly() {
        ProviderBadgeService.IssuedBadge issued = issueBadge();

        String payload = signingService.verify(issued.qrPayload());
        assertThat(payload).contains(issued.badgeSerial())
                .contains(provider.getProviderPublicId())
                .doesNotContain("impiloHealthId", "nationalId");
    }

    @Test
    void validScanReturnsLiveRegisterFacts() {
        ProviderBadgeService.IssuedBadge issued = issueBadge();
        when(badgeRepository.findByBadgeSerial(issued.badgeSerial()))
                .thenReturn(Optional.of(storedBadge));
        when(providerRepository.findById(42L)).thenReturn(Optional.of(provider));

        ProviderBadgeService.BadgeVerification result = service.verifyScan(issued.qrPayload());

        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.displayName()).contains("Moyo");
        assertThat(result.profession()).isEqualTo("NURSE");
    }

    @Test
    void forgedRevokedAndNotInStandingAllCollapseToOneShape() {
        ProviderBadgeService.IssuedBadge issued = issueBadge();

        // Forged: signature from a different seed.
        BadgeSigningService otherKey = new BadgeSigningService("attacker-seed");
        ProviderBadgeService.BadgeVerification forged =
                service.verifyScan(otherKey.sign("{\"serial\":\"" + issued.badgeSerial() + "\"}"));

        // Revoked serial.
        storedBadge.setStatus(ProviderBadgeEntity.STATUS_REVOKED);
        when(badgeRepository.findByBadgeSerial(issued.badgeSerial()))
                .thenReturn(Optional.of(storedBadge));
        ProviderBadgeService.BadgeVerification revoked = service.verifyScan(issued.qrPayload());

        // Not-in-standing provider behind an active serial.
        storedBadge.setStatus(ProviderBadgeEntity.STATUS_ACTIVE);
        provider.setLifecycleStatus("SUSPENDED");
        provider.deriveStatusProjections();
        when(providerRepository.findById(42L)).thenReturn(Optional.of(provider));
        ProviderBadgeService.BadgeVerification suspended = service.verifyScan(issued.qrPayload());

        assertThat(forged.status()).isEqualTo("NOT_RECOGNISED");
        assertThat(revoked).isEqualTo(forged);
        assertThat(suspended).isEqualTo(forged);
    }

    @Test
    void revocationTouchesTheSerialOnly() {
        ProviderBadgeService.IssuedBadge issued = issueBadge();
        when(badgeRepository.findByBadgeSerial(issued.badgeSerial()))
                .thenReturn(Optional.of(storedBadge));

        ProviderBadgeEntity revoked = service.revoke(issued.badgeSerial(), "LOST", "left in taxi");

        assertThat(revoked.getStatus()).isEqualTo(ProviderBadgeEntity.STATUS_LOST);
        // The provider record itself is never mutated by a badge loss.
        assertThat(provider.getLifecycleStatus()).isEqualTo("LICENCED_ACTIVE");
    }

    @Test
    void signingSurvivesInstanceRestart() {
        ProviderBadgeService.IssuedBadge issued = issueBadge();
        BadgeSigningService secondInstance = new BadgeSigningService("unit-test-seed");
        assertThat(secondInstance.verify(issued.qrPayload())).isNotNull();
    }
}
