package zw.gov.mohcc.impilo.ubomi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.BirthNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.DeathNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.VerificationLogEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.BirthNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.DeathNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.VerificationLogRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Vital event verification service.
 *
 * Looks up a civil registration number against both birth and death
 * notification tables to verify registration status. Every verification
 * request is logged for audit purposes.
 *
 * This is the primary interoperability endpoint for external CRVS systems.
 */
@Service
public class VerificationService {

    private final BirthNotificationRepository birthRepository;
    private final DeathNotificationRepository deathRepository;
    private final VerificationLogRepository verificationLogRepository;

    public VerificationService(BirthNotificationRepository birthRepository,
                                DeathNotificationRepository deathRepository,
                                VerificationLogRepository verificationLogRepository) {
        this.birthRepository = birthRepository;
        this.deathRepository = deathRepository;
        this.verificationLogRepository = verificationLogRepository;
    }

    /**
     * Verify a vital event registration by registration number.
     * Searches both birth and death notifications. Logs every verification attempt.
     *
     * @return the verification result with event type and status information
     */
    @Transactional
    public VerificationResult verify(UUID tenantId, String registrationNumber,
                                      String requestedByActor, String accessMode) {
        // Search birth notifications first
        Optional<BirthNotificationEntity> birth =
                birthRepository.findByTenantIdAndNotificationNumber(tenantId, registrationNumber);
        if (birth.isPresent()) {
            BirthNotificationEntity b = birth.get();
            VerificationResult result = new VerificationResult(
                    "BIRTH", "VERIFIED", b.getStatus(), b.getNotificationNumber(), b.getCivilRegNumber());
            logVerification(tenantId, registrationNumber, "BIRTH", requestedByActor, accessMode, "VERIFIED",
                    String.format("{\"status\":\"%s\",\"civilRegNumber\":\"%s\"}", b.getStatus(), b.getCivilRegNumber()));
            return result;
        }

        // Search death notifications
        Optional<DeathNotificationEntity> death =
                deathRepository.findByTenantIdAndNotificationNumber(tenantId, registrationNumber);
        if (death.isPresent()) {
            DeathNotificationEntity d = death.get();
            VerificationResult result = new VerificationResult(
                    "DEATH", "VERIFIED", d.getStatus(), d.getNotificationNumber(), d.getCivilRegNumber());
            logVerification(tenantId, registrationNumber, "DEATH", requestedByActor, accessMode, "VERIFIED",
                    String.format("{\"status\":\"%s\",\"civilRegNumber\":\"%s\"}", d.getStatus(), d.getCivilRegNumber()));
            return result;
        }

        // Not found
        logVerification(tenantId, registrationNumber, "UNKNOWN", requestedByActor, accessMode, "NOT_FOUND", null);
        return new VerificationResult(null, "NOT_FOUND", null, registrationNumber, null);
    }

    private void logVerification(UUID tenantId, String registrationNumber, String eventType,
                                  String requestedByActor, String accessMode,
                                  String verificationResult, String responseSummary) {
        VerificationLogEntity log = new VerificationLogEntity();
        log.setTenantId(tenantId);
        log.setRegistrationNumber(registrationNumber);
        log.setEventType(eventType);
        log.setRequestedByActor(requestedByActor);
        log.setAccessMode(accessMode);
        log.setVerificationResult(verificationResult);
        log.setResponseSummary(responseSummary);
        verificationLogRepository.save(log);
    }

    /**
     * Result of a vital event verification lookup.
     */
    public record VerificationResult(
            String eventType,
            String verificationResult,
            String notificationStatus,
            String registrationNumber,
            String civilRegNumber
    ) {}
}
