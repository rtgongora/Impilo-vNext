package zw.gov.mohcc.impilo.vito.core.pickup;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.vito.core.PickupStatus;
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

    public DelegatedPickupService(DelegatedPickupRepository pickupRepo,
                                   HmacService hmacService,
                                   Argon2IdService argon2IdService,
                                   EventOutboxRepository outboxRepo) {
        this.pickupRepo = pickupRepo;
        this.hmacService = hmacService;
        this.argon2IdService = argon2IdService;
        this.outboxRepo = outboxRepo;
    }

    /**
     * Create a delegated pickup package.
     * Returns the plaintext OTP and pickup token (only returned once, never stored).
     */
    @Transactional
    public PickupPackage create(UUID tenantId, Long issuanceRequestId, String delegateName,
                                 String delegateContact, String delegateIdRef,
                                 UUID facilityId, int expiryHours) {
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
        pickup.setExpiresAt(OffsetDateTime.now().plusHours(expiryHours));
        pickup.setStatus(PickupStatus.ACTIVE);
        pickup = pickupRepo.save(pickup);

        publishEvent("PICKUP", pickup.getId().toString(), "vito.pickup.created",
                "{\"tenantId\":\"" + tenantId + "\",\"issuanceRequestId\":" + issuanceRequestId + "}");

        return new PickupPackage(pickup.getId(), pickupToken, otp, pickup.getExpiresAt());
    }

    /**
     * Redeem a delegated pickup at a facility.
     * Verifies the QR token AND the OTP. Max attempts enforced.
     */
    @Transactional
    public DelegatedPickupEntity redeem(String pickupToken, String otp, String redeemedBy) {
        String tokenHash = hmacService.computeLookupHash(pickupToken);
        DelegatedPickupEntity pickup = pickupRepo.findByPickupTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid pickup token"));

        // Check status
        if (pickup.getStatus() != PickupStatus.ACTIVE) {
            throw new IllegalStateException("Pickup is not active: " + pickup.getStatus());
        }

        // Check expiry
        if (OffsetDateTime.now().isAfter(pickup.getExpiresAt())) {
            pickup.setStatus(PickupStatus.EXPIRED);
            pickupRepo.save(pickup);
            throw new IllegalStateException("Pickup has expired");
        }

        // Increment attempt count
        pickup.setAttempts(pickup.getAttempts() + 1);

        // Verify OTP
        if (!argon2IdService.verify(otp, pickup.getOtpHash())) {
            if (pickup.getAttempts() >= pickup.getMaxAttempts()) {
                pickup.setStatus(PickupStatus.REVOKED);
                pickupRepo.save(pickup);
                throw new IllegalStateException("Max attempts exceeded, pickup revoked");
            }
            pickupRepo.save(pickup);
            throw new IllegalArgumentException("Invalid OTP (" + pickup.getAttempts() + "/" + pickup.getMaxAttempts() + " attempts)");
        }

        // Success
        pickup.setStatus(PickupStatus.REDEEMED);
        pickup.setRedeemedAt(OffsetDateTime.now());
        pickup.setRedeemedBy(redeemedBy);
        pickup = pickupRepo.save(pickup);

        publishEvent("PICKUP", pickup.getId().toString(), "vito.pickup.redeemed",
                "{\"tenantId\":\"" + pickup.getTenantId() + "\",\"redeemedBy\":\"" + redeemedBy + "\"}");

        return pickup;
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
}
