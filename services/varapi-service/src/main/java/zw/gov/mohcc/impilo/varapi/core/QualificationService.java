package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderQualificationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderQualificationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing provider qualifications and their verification.
 */
@Service
public class QualificationService {

    private static final Logger log = LoggerFactory.getLogger(QualificationService.class);

    private final ProviderQualificationRepository qualificationRepository;
    private final ProviderRepository providerRepository;
    private final EventOutboxRepository outboxRepository;

    public QualificationService(
            ProviderQualificationRepository qualificationRepository,
            ProviderRepository providerRepository,
            EventOutboxRepository outboxRepository) {
        this.qualificationRepository = qualificationRepository;
        this.providerRepository = providerRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Add a qualification to a provider.
     */
    @Transactional
    public ProviderQualificationEntity addQualification(
            Long providerId,
            String qualificationType,
            String title,
            String institutionName,
            String country,
            LocalDate awardDate,
            Long evidenceDocumentId,
            String remarks) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Adding qualification: providerId={}, type={}, title={}, actor={}",
                providerId, qualificationType, title, ctx.actorId());

        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        ProviderQualificationEntity qualification = new ProviderQualificationEntity();
        qualification.setProvider(provider);
        qualification.setTenantId(ctx.tenantId());
        qualification.setQualificationType(qualificationType);
        qualification.setTitle(title);
        qualification.setInstitutionName(institutionName);
        qualification.setCountry(country);
        qualification.setAwardDate(awardDate);
        qualification.setEvidenceDocumentId(evidenceDocumentId);
        qualification.setVerificationStatus("PENDING");
        qualification.setRemarks(remarks);

        qualification = qualificationRepository.save(qualification);

        log.info("Qualification added: id={}, providerId={}", qualification.getId(), providerId);
        publishEvent("PROVIDER_QUALIFICATION", qualification.getId().toString(),
                "varapi.qualification.created",
                String.format("{\"qualificationId\":%d,\"providerId\":%d,\"type\":\"%s\",\"title\":\"%s\"}",
                        qualification.getId(), providerId, qualificationType, title));

        return qualification;
    }

    /**
     * Verify a qualification.
     */
    @Transactional
    public ProviderQualificationEntity verifyQualification(
            Long qualificationId,
            String verificationStatus,
            String verifiedBy,
            String remarks) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Verifying qualification: id={}, status={}, actor={}",
                qualificationId, verificationStatus, ctx.actorId());

        ProviderQualificationEntity qualification = qualificationRepository.findById(qualificationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification not found: " + qualificationId));

        if (!"PENDING".equals(qualification.getVerificationStatus())) {
            throw new IllegalStateException("Can only verify pending qualifications");
        }

        if (!"VERIFIED".equals(verificationStatus) && !"FAILED".equals(verificationStatus) && !"NOT_REQUIRED".equals(verificationStatus)) {
            throw new IllegalArgumentException("Invalid verification status: " + verificationStatus);
        }

        qualification.setVerificationStatus(verificationStatus);
        qualification.setVerifiedBy(verifiedBy != null ? verifiedBy : ctx.actorId());
        qualification.setVerifiedAt(Instant.now());
        qualification.setRemarks(remarks != null ? remarks : qualification.getRemarks());

        qualification = qualificationRepository.save(qualification);

        log.info("Qualification verified: id={}, status={}", qualificationId, verificationStatus);
        publishEvent("PROVIDER_QUALIFICATION", qualification.getId().toString(),
                "varapi.qualification.verified",
                String.format("{\"qualificationId\":%d,\"status\":\"%s\"}",
                        qualificationId, verificationStatus));

        return qualification;
    }

    /**
     * Get qualification by ID.
     */
    @Transactional(readOnly = true)
    public ProviderQualificationEntity getQualification(Long qualificationId) {
        return qualificationRepository.findById(qualificationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification not found: " + qualificationId));
    }

    /**
     * Get all qualifications for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderQualificationEntity> getQualificationsByProvider(Long providerId) {
        return qualificationRepository.findByProviderId(providerId);
    }

    /**
     * Get verified qualifications for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderQualificationEntity> getVerifiedQualifications(Long providerId) {
        return qualificationRepository.findByProviderIdAndVerificationStatus(providerId, "VERIFIED");
    }

    /**
     * Get pending qualifications for verification.
     */
    @Transactional(readOnly = true)
    public List<ProviderQualificationEntity> getPendingQualifications() {
        TrustContext ctx = TrustContextHolder.require();
        return qualificationRepository.findByTenantIdAndVerificationStatus(ctx.tenantId(), "PENDING");
    }

    /**
     * Create a new qualification (from controller).
     */
    @Transactional
    public ProviderQualificationEntity createQualification(
            Long providerId,
            String qualificationName,
            String qualificationCode,
            String awardingBody,
            LocalDate dateAwarded,
            String qualificationLevel,
            String professionalCategory,
            String documentReference,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating qualification: providerId={}, name={}, awardingBody={}", providerId, qualificationName, awardingBody);

        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        ProviderQualificationEntity qualification = new ProviderQualificationEntity();
        qualification.setProvider(provider);
        qualification.setTenantId(ctx.tenantId());
        qualification.setTitle(qualificationName);
        qualification.setQualificationCode(qualificationCode);
        qualification.setInstitutionName(awardingBody);
        qualification.setAwardDate(dateAwarded);
        qualification.setQualificationLevel(qualificationLevel);
        qualification.setProfessionalCategory(professionalCategory);
        qualification.setEvidenceDocumentId(documentReference != null ? Long.parseLong(documentReference) : null);
        qualification.setVerificationStatus("PENDING");
        qualification.setRemarks(notes);
        qualification.setVersion(1);
        qualification.setCreatedBy(ctx.actorId());
        qualification.setUpdatedBy(ctx.actorId());

        qualification = qualificationRepository.save(qualification);
        log.info("Qualification created: id={}", qualification.getId());
        return qualification;
    }

    /**
     * Submit qualification for verification.
     */
    @Transactional
    public ProviderQualificationEntity submitForVerification(Long qualificationId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Submitting qualification for verification: id={}", qualificationId);

        ProviderQualificationEntity qualification = qualificationRepository.findById(qualificationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification not found: " + qualificationId));

        if (!"PENDING".equals(qualification.getVerificationStatus())) {
            throw new IllegalStateException("Can only submit pending qualifications");
        }

        qualification.setVersion(qualification.getVersion() + 1);
        qualification.setUpdatedBy(ctx.actorId());

        return qualificationRepository.save(qualification);
    }

    /**
     * Complete verification of a qualification.
     */
    @Transactional
    public ProviderQualificationEntity completeVerification(
            Long qualificationId,
            String verificationStatus,
            LocalDate verificationDate,
            String verifiedBy,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Completing verification: id={}, status={}", qualificationId, verificationStatus);

        ProviderQualificationEntity qualification = qualificationRepository.findById(qualificationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification not found: " + qualificationId));

        qualification.setVerificationStatus(verificationStatus);
        qualification.setVerificationDate(verificationDate);
        qualification.setVerifiedBy(verifiedBy != null ? verifiedBy : ctx.actorId());
        qualification.setVerificationNotes(notes);
        qualification.setVersion(qualification.getVersion() + 1);
        qualification.setUpdatedBy(ctx.actorId());

        qualification = qualificationRepository.save(qualification);
        log.info("Qualification verification completed: id={}, status={}", qualificationId, verificationStatus);
        return qualification;
    }

    /**
     * Supersede a qualification with a new one.
     */
    @Transactional
    public ProviderQualificationEntity supersedeQualification(Long oldQualificationId, Long newQualificationId, String reason) {
        log.info("Superseding qualification: oldId={}, newId={}", oldQualificationId, newQualificationId);

        ProviderQualificationEntity oldQualification = qualificationRepository.findById(oldQualificationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification not found: " + oldQualificationId));

        oldQualification.setVerificationStatus("SUPERSEDED");
        oldQualification.setRemarks("Superseded by qualification " + newQualificationId + ": " + reason);

        ProviderQualificationEntity newQualification = qualificationRepository.findById(newQualificationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification not found: " + newQualificationId));

        newQualification.setSupersedesId(oldQualificationId);

        qualificationRepository.save(oldQualification);
        qualificationRepository.save(newQualification);

        return newQualification;
    }

    /**
     * Get pending verification qualifications for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderQualificationEntity> getPendingVerificationQualifications(Long providerId) {
        return qualificationRepository.findByProviderIdAndVerificationStatus(providerId, "PENDING");
    }

    /**
     * Search qualifications by criteria.
     */
    @Transactional(readOnly = true)
    public List<ProviderQualificationEntity> searchQualifications(
            String awardingBody,
            String qualificationLevel,
            String professionalCategory) {
        if (awardingBody != null) {
            return qualificationRepository.findByAwardingBody(awardingBody);
        }
        if (qualificationLevel != null) {
            return qualificationRepository.findByQualificationLevel(qualificationLevel);
        }
        return qualificationRepository.findAll();
    }

    /**
     * Get all pending verification qualifications.
     */
    @Transactional(readOnly = true)
    public List<ProviderQualificationEntity> getPendingVerification() {
        return qualificationRepository.findByVerificationStatusIn(List.of("PENDING", "SUBMITTED"));
    }

    /**
     * Delete a qualification (only if not verified).
     */
    @Transactional
    public void deleteQualification(Long qualificationId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Deleting qualification: id={}, actor={}", qualificationId, ctx.actorId());

        ProviderQualificationEntity qualification = qualificationRepository.findById(qualificationId)
                .orElseThrow(() -> new IllegalArgumentException("Qualification not found: " + qualificationId));

        if ("VERIFIED".equals(qualification.getVerificationStatus())) {
            throw new IllegalStateException("Cannot delete verified qualifications");
        }

        qualificationRepository.delete(qualification);
        log.info("Qualification deleted: id={}", qualificationId);
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}