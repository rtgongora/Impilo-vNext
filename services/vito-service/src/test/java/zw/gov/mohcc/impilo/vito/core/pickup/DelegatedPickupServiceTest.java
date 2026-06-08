package zw.gov.mohcc.impilo.vito.core.pickup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.vito.core.PickupStatus;
import zw.gov.mohcc.impilo.vito.core.biometric.policy.BiometricPolicyClient;
import zw.gov.mohcc.impilo.vito.persistence.entity.DelegatedPickupEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.DelegatedPickupRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DelegatedPickupServiceTest {

    @Mock private DelegatedPickupRepository pickupRepo;
    @Mock private HmacService hmacService;
    @Mock private Argon2IdService argon2IdService;
    @Mock private EventOutboxRepository outboxRepo;
    @Mock private BiometricPolicyClient biometricPolicyClient;

    private DelegatedPickupService service;

    private DelegatedPickupEntity pickup;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DelegatedPickupService(
                pickupRepo, hmacService, argon2IdService, outboxRepo, biometricPolicyClient, false);
        pickup = new DelegatedPickupEntity();
        pickup.setId(1L);
        pickup.setTenantId(tenantId);
        pickup.setIssuanceRequestId(42L);
        pickup.setDelegateName("Jane Delegate");
        pickup.setFacilityId(facilityId);
        pickup.setFacilityName("Harare Central Hospital");
        pickup.setExpiresAt(OffsetDateTime.now().plusHours(2));
        pickup.setStatus(PickupStatus.ACTIVE);
        pickup.setOtpHash("hash");
        pickup.setMaxAttempts(3);
        pickup.setAttempts(0);

        when(hmacService.computeLookupHash("token-1")).thenReturn("token-hash");
        when(pickupRepo.findByPickupTokenHash("token-hash")).thenReturn(Optional.of(pickup));
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void verifyPickupReturnsDetailsWithoutRedeeming() {
        when(argon2IdService.verify("123456", "hash")).thenReturn(true);

        DelegatedPickupService.PickupVerification result = service.verifyPickup("token-1", "123456");

        assertEquals("Jane Delegate", result.delegateName());
        assertEquals(facilityId, result.facilityId());
        assertEquals("Harare Central Hospital", result.facilityName());
        assertEquals(42L, result.issuanceRequestId());
        assertEquals(PickupStatus.ACTIVE, pickup.getStatus());
        assertEquals(0, pickup.getAttempts());
        verify(pickupRepo, never()).save(pickup);
    }

    @Test
    void verifyPickupIncrementsAttemptsOnInvalidOtp() {
        when(argon2IdService.verify("000000", "hash")).thenReturn(false);
        when(pickupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(IllegalArgumentException.class, () -> service.verifyPickup("token-1", "000000"));
        assertEquals(1, pickup.getAttempts());
        assertEquals(PickupStatus.ACTIVE, pickup.getStatus());
    }

    @Test
    void redeemMarksPickupRedeemed() {
        when(argon2IdService.verify("123456", "hash")).thenReturn(true);
        when(pickupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DelegatedPickupEntity redeemed = service.redeem("token-1", "123456", "staff-1");

        assertEquals(PickupStatus.REDEEMED, redeemed.getStatus());
        assertEquals("staff-1", redeemed.getRedeemedBy());
        assertNotNull(redeemed.getRedeemedAt());
    }
}
