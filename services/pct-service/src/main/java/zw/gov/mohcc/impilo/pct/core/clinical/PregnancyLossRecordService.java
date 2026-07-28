package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialityCategory;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyLossRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyLossRecordRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Records a pregnancy loss on the mother's episode.
 *
 * <p>The clinical and doctrinal invariants — a stillbirth mints no person, a certificate needs its
 * civil-notification event, a termination needs its lawful authorisation — are enforced below the
 * service by CHECK constraints, so they hold even against a write that bypasses this service. What
 * the service adds is the offline idempotency and the default confidentiality category.
 *
 * <p>Loss records ship at {@code FULL_CLINICAL} with the SPECIALLY_PROTECTED gap stated: the
 * dedicated confidentiality pass (a separate lane) will stamp and enforce; this service does not
 * wait on it, exactly as the HIV programme shipped in W3.
 */
@Service
public class PregnancyLossRecordService {

    private static final Logger log = LoggerFactory.getLogger(PregnancyLossRecordService.class);

    private final PregnancyLossRecordRepository losses;

    private final ConfidentialCarePolicyProvider confidentiality;

    public PregnancyLossRecordService(PregnancyLossRecordRepository losses,
                                      ConfidentialCarePolicyProvider confidentiality) {
        this.losses = losses;
        this.confidentiality = confidentiality;
    }

    /**
     * A TERMINATION loss is confidential at any age, for the same reason the TOP record is. Every
     * other loss — miscarriage, stillbirth, ectopic, molar — is confidential only for a young person
     * below the governed age, and whether even that holds is itself a governed parameter
     * (SRH_NON_TERMINATION_LOSS_CONFIDENTIAL_FROM_GUARDIAN), because bereavement support frequently
     * involves the guardian and the protective answer is genuinely less obvious here.
     */
    private ConfidentialityStamper.Stamp stampFor(PregnancyLossRecordEntity loss) {
        var actDate = loss.getOccurredOn() == null ? java.time.LocalDate.now() : loss.getOccurredOn();
        var policy = confidentiality.policyAsOf(loss.getTenantId(), actDate);
        var category = ConfidentialityCategory.SEXUAL_REPRODUCTIVE_HEALTH;

        ConfidentialityStamper.Stamp decided;
        if (PregnancyLossRecordEntity.TYPE_TERMINATION.equals(loss.getLossType())) {
            decided = ConfidentialityStamper.alwaysConfidential(category, policy);
        } else if (Boolean.FALSE.equals(policy.nonTerminationLossConfidentialFromGuardian().orElse(null))) {
            // Policy says a non-termination loss is NOT confidential from a guardian. The category is
            // still recorded — it is true content classification — but no protection is claimed.
            decided = new ConfidentialityStamper.Stamp(category.code(),
                    ConfidentialityStamper.CLASS_FULL_CLINICAL,
                    ConfidentialityStamper.BASIS_SUBJECT_UNDER_POLICY_AGE, policy.contentVersion());
        } else {
            var subject = confidentiality.subjectAt(loss.getTenantId(), loss.getMotherCpid(), category, actDate);
            decided = ConfidentialityStamper.ageGated(category, subject, actDate, policy);
        }
        return ConfidentialityStamper.gated(decided, confidentiality.classStampingEnabled());
    }

    @Transactional(readOnly = true)
    public List<PregnancyLossRecordEntity> forMother(UUID tenantId, String motherCpid) {
        if (tenantId == null || motherCpid == null || motherCpid.isBlank()) {
            return List.of();
        }
        return losses.findByMother(tenantId, motherCpid);
    }

    @Transactional
    public PregnancyLossRecordEntity record(PregnancyLossRecordEntity loss) {
        if (loss.getClientOfflineId() != null && !loss.getClientOfflineId().isBlank()) {
            Optional<PregnancyLossRecordEntity> replayed = losses.findByTenantIdAndClientOfflineId(
                    loss.getTenantId(), loss.getClientOfflineId());
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }
        if (loss.getLossRecordId() == null) {
            loss.setLossRecordId(UUID.randomUUID());
        }
        if (loss.getRecordedAt() == null) {
            loss.setRecordedAt(OffsetDateTime.now());
        }
        var stamp = stampFor(loss);
        loss.setSensitivityClass(stamp.sensitivityClass());
        loss.setConfidentialityCategory(stamp.category());
        loss.setConfidentialityBasis(stamp.basis());
        loss.setConfidentialityPolicyVersion(stamp.policyVersion());
        // Defaults for the boolean gates, so a null never reads as an affirmative.
        if (loss.getStillbirthCertifiable() == null) {
            loss.setStillbirthCertifiable(Boolean.FALSE);
        }
        if (loss.getCertificateIssued() == null) {
            loss.setCertificateIssued(Boolean.FALSE);
        }
        if (loss.getBereavementSupportOffered() == null) {
            loss.setBereavementSupportOffered(Boolean.FALSE);
        }
        if (loss.getPostlossContraceptionDiscussed() == null) {
            loss.setPostlossContraceptionDiscussed(Boolean.FALSE);
        }
        return losses.save(loss);
    }
}
