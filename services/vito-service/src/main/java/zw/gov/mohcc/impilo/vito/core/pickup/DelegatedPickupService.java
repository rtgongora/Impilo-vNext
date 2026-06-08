package zw.gov.mohcc.impilo.vito.core.pickup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.vito.core.PickupStatus;
import zw.gov.mohcc.impilo.vito.core.biometric.BiometricPolicyDeniedException;
import zw.gov.mohcc.impilo.vito.core.biometric.policy.BiometricPolicyClient;
import zw.gov.mohcc.impilo.vito.core.biometric.policy.TshepoBiometricPolicyResponse;
import zw.gov.mohcc.impilo.vito.persistence.entity.DelegatedPickupEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.DelegatedPickupRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class DelegatedPickupService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;

    private final DelegatedPickupRepository pickupRepo;
    private final HmacService hmacService;
    private final Argon2IdService argon2IdService;
    private final EventOutboxRepository outboxRepo;
    private final BiometricPolicyClient biometricPolicyClient;
    private final boolean biometricPolicyOnRedeem;

    public DelegatedPickupService(DelegatedPickupRepository pickupRepo,
                                   HmacService hmacService,
                                   Argon2IdService argon2IdService,
                                   EventOutboxRepository outboxRepo,
                                   BiometricPolicyClient biometricPolicyClient,
                                   @Value("${vito.pickup.biometric-policy-on-redeem:false}")
                                   boolean biometricPolicyOnRedeem) {
        this.pickupRepo = pickupRepo;
        this.hmacService = hmacService;
        this.argon2IdService = argon2IdService;
        this.outboxRepo = outboxRepo;
        this.biometricPolicyClient = biometricPolicyClient;
        this.biometricPolicyOnRedeem = biometricPolicyOnRedeem;
    }

    /**
     * Create a delegated pickup package.
     * Returns the plaintext OTP and pickup token (only returned once, never stored).
     */
    @Transactional
    public PickupPackage create(UUID tenantId, Long issuanceRequestId, String delegateName,
                                 String delegateContact, String delegateIdRef,
                                 UUID facilityId, String facilityName, int expiryHours) {
        // Generate pickup token (UUID for QR) and 6-digit OTP
        String pickupToken = UUID.randomUUID().toString();
        String otp = generateOtp();

        // Hash for storage (never store plaintext)
        String tokenHash = hmacService.computeLookupHash(pickupToken);
        String otpHashValue = argon2IdService.hash(otp);

        DelegatedPickupEntity pickup = new DelegatedPickupEntity();
        pickup.setTenantId(tenantId);
        pickup.setIssuanceRequestId(issuanceRequestId);
        pickup.setPickupTokenHash(tokenHash);
        pickup.setOtpHash(otpHashValue);
        pickup.setDelegateName(delegateName);
        pickup.setDelegateContact(delegateContact != null ? delegateContact : "{}");
        pickup.setDelegateIdRef(delegateIdRef);
        pickup.setFacilityId(facilityId);
        pickup.setFacilityName(facilityName != null ? facilityName.trim() : null);
        pickup.setExpiresAt(OffsetDateTime.now().plusHours(expiryHours));
        pickup.setStatus(PickupStatus.ACTIVE);
        pickup = pickupRepo.save(pickup);

        publishEvent("PICKUP", pickup.getId().toString(), "vito.pickup.created",
                "{\"tenantId\":\"" + tenantId + "\",\"issuanceRequestId\":" + issuanceRequestId + "}");

        return new PickupPackage(pickup.getId(), pickupToken, otp, pickup.getExpiresAt());
    }

    /**
     * Verify a delegated pickup token and OTP without marking the pickup redeemed.
     * Used by facility staff before physical card handover.
     */
    @Transactional
    public PickupVerification verifyPickup(String pickupToken, String otp) {
        DelegatedPickupEntity pickup = resolveActivePickup(pickupToken);
        assertOtpValid(pickup, otp, false);
        return new PickupVerification(
                pickup.getDelegateName(),
                pickup.getFacilityId(),
                pickup.getFacilityName(),
                pickup.getIssuanceRequestId(),
                pickup.getExpiresAt());
    }

    /**
     * Redeem a delegated pickup at a facility.
     * Verifies the QR token AND the OTP. Max attempts enforced.
     */
    @Transactional
    public DelegatedPickupEntity redeem(String pickupToken, String otp, String redeemedBy) {
        DelegatedPickupEntity pickup = resolveActivePickup(pickupToken);

        if (biometricPolicyOnRedeem) {
            TshepoBiometricPolicyResponse policy = biometricPolicyClient.evaluate(
                    "CLIENT", "DELEGATED_PICKUP", "FACILITY", null, "VERIFY");
            if (policy == null
                    || !policy.verificationAllowed()
                    || "PROHIBITED".equalsIgnoreCase(policy.policyOutcome())) {
                throw new BiometricPolicyDeniedException(
                        "Delegated pickup redemption denied by Tshepo biometric policy");
            }
        }

        assertOtpValid(pickup, otp, true);

        pickup.setStatus(PickupStatus.REDEEMED);
        pickup.setRedeemedAt(OffsetDateTime.now());
        pickup.setRedeemedBy(redeemedBy);
        pickup = pickupRepo.save(pickup);

        publishEvent("PICKUP", pickup.getId().toString(), "vito.pickup.redeemed",
                "{\"tenantId\":\"" + pickup.getTenantId() + "\",\"redeemedBy\":\"" + redeemedBy + "\"}");

        return pickup;
    }

    private DelegatedPickupEntity resolveActivePickup(String pickupToken) {
        String tokenHash = hmacService.computeLookupHash(pickupToken);
        DelegatedPickupEntity pickup = pickupRepo.findByPickupTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid pickup token"));

        if (pickup.getStatus() != PickupStatus.ACTIVE) {
            throw new IllegalStateException("Pickup is not active: " + pickup.getStatus());
        }

        if (OffsetDateTime.now().isAfter(pickup.getExpiresAt())) {
            pickup.setStatus(PickupStatus.EXPIRED);
            pickupRepo.save(pickup);
            throw new IllegalStateException("Pickup has expired");
        }
        return pickup;
    }

    private void assertOtpValid(DelegatedPickupEntity pickup, String otp, boolean incrementOnSuccess) {
        if (!argon2IdService.verify(otp, pickup.getOtpHash())) {
            pickup.setAttempts(pickup.getAttempts() + 1);
            if (pickup.getAttempts() >= pickup.getMaxAttempts()) {
                pickup.setStatus(PickupStatus.REVOKED);
                pickupRepo.save(pickup);
                throw new IllegalStateException("Max attempts exceeded, pickup revoked");
            }
            pickupRepo.save(pickup);
            throw new IllegalArgumentException("Invalid OTP (" + pickup.getAttempts() + "/" + pickup.getMaxAttempts() + " attempts)");
        }
        if (incrementOnSuccess) {
            pickup.setAttempts(pickup.getAttempts() + 1);
        }
    }

    @Transactional(readOnly = true)
    public Page<DelegatedPickupEntity> listActive(UUID tenantId, Pageable pageable) {
        return pickupRepo.findByTenantIdAndStatus(tenantId, PickupStatus.ACTIVE, pageable);
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepo.save(event);
    }

    public record PickupPackage(Long pickupId, String pickupToken, String otp, OffsetDateTime expiresAt) {}

    public record PickupVerification(
            String delegateName,
            UUID facilityId,
            String facilityName,
            Long issuanceRequestId,
            OffsetDateTime expiresAt) {}
}
