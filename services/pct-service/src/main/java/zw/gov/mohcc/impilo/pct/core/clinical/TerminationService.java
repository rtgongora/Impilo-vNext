package zw.gov.mohcc.impilo.pct.core.clinical;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.TopAuthorisationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TopProcedureEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.TopAuthorisationRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TopProcedureRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lawful termination of pregnancy: authorise, then perform.
 *
 * <p>The service enforces nothing the database does not already: an unauthorised procedure is
 * impossible because the composite FK (V435) refuses it, and an authorisation cannot leave AUTHORISED
 * while a procedure references it. The service exists to give those a clean flow and to set the
 * fixed {@code authorisation_status = AUTHORISED} on the procedure. There is deliberately NO event
 * emission here and none anywhere — TOP is aggregate-only by PO ruling, and the
 * check-top-no-record-level-emit guard fails the build if a TOP record ever reaches an outbox path.
 */
@Service
public class TerminationService {

    private final TopAuthorisationRepository authorisations;
    private final TopProcedureRepository procedures;

    public TerminationService(TopAuthorisationRepository authorisations,
                              TopProcedureRepository procedures) {
        this.authorisations = authorisations;
        this.procedures = procedures;
    }

    @Transactional
    public TopAuthorisationEntity authorise(TopAuthorisationEntity a) {
        if (a.getAuthorisationId() == null) {
            a.setAuthorisationId(UUID.randomUUID());
        }
        if (a.getRecordedAt() == null) {
            a.setRecordedAt(OffsetDateTime.now());
        }
        if (a.getConsentGiven() == null) {
            a.setConsentGiven("NOT_RECORDED");
        }
        if (a.getSensitivityClass() == null) {
            a.setSensitivityClass("FULL_CLINICAL");
        }
        return authorisations.save(a);
    }

    /**
     * Record a procedure. Fixes {@code authorisation_status = AUTHORISED}; the composite FK then
     * refuses the write unless the referenced authorisation is genuinely AUTHORISED. A procedure
     * against a non-authorised or non-existent authorisation surfaces as a data-integrity error
     * from the database, never a silently accepted record.
     */
    @Transactional
    public TopProcedureEntity recordProcedure(TopProcedureEntity p) {
        if (p.getProcedureId() == null) {
            p.setProcedureId(UUID.randomUUID());
        }
        p.setAuthorisationStatus(TopAuthorisationEntity.STATUS_AUTHORISED);
        if (p.getRecordedAt() == null) {
            p.setRecordedAt(OffsetDateTime.now());
        }
        if (p.getSensitivityClass() == null) {
            p.setSensitivityClass("FULL_CLINICAL");
        }
        return procedures.save(p);
    }
}
