package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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

    public PregnancyLossRecordService(PregnancyLossRecordRepository losses) {
        this.losses = losses;
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
        if (loss.getConfidentialityCategory() == null) {
            loss.setConfidentialityCategory("FULL_CLINICAL");
        }
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
