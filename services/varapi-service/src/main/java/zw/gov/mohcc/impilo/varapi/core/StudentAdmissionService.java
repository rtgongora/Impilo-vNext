package zw.gov.mohcc.impilo.varapi.core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Admission to the Student Register (NCZ-W1C): the moment an application becomes an entry on a
 * statutory register, with a number attached to a real person.
 *
 * <p>It is deliberately one transaction over three things that must not drift apart — the fee
 * verdict, the issued number, and the register entry. The failure this shape prevents is an
 * applicant who holds an index number that no register entry mentions, or an entry with no number,
 * either of which is a person whose standing cannot be answered from the record.</p>
 *
 * <p>Order matters and is not arbitrary. The fee is checked FIRST, before a number is even
 * reserved: a reserved number that is then abandoned is a permanent gap in the sequence, and gaps
 * invite the question "who was 47?" for the rest of the register's life. The number is RESERVED
 * before the entry is written and only marked ISSUED once the entry exists, so a failure in
 * between leaves a reservation that can be released with a reason rather than a number handed out
 * for an admission that never happened.</p>
 */
@Service
public class StudentAdmissionService {

    private static final Logger log = LoggerFactory.getLogger(StudentAdmissionService.class);

    private final CouncilFeeGate feeGate;
    private final StudentApplicationService applicationService;

    @PersistenceContext
    private EntityManager entityManager;

    public StudentAdmissionService(CouncilFeeGate feeGate,
                                   StudentApplicationService applicationService) {
        this.feeGate = feeGate;
        this.applicationService = applicationService;
    }

    public record Admission(String indexNumber, Long registerEntryId, String registerCode,
                            LocalDate effectiveFrom) {
    }

    /**
     * Admit an applicant to a register.
     *
     * @param feeCode  the fee that must be settled first. An unset fee blocks here — it does not
     *                 wave the applicant through, which is the whole point of {@link CouncilFeeGate}.
     */
    @Transactional
    public Admission admit(UUID tenantId, Long councilId, Long applicationId, Long providerId,
                           String registerCode, String policyKey, String feeCode, String decidedBy) {

        if (!applicationService.isComplete(tenantId, applicationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This application still has sections that are not complete. Admitting on an "
                            + "incomplete application would put someone on a statutory register "
                            + "without the evidence the council asked for.");
        }

        // FIRST. A fee the Council has not set stops the admission before a number is burned.
        feeGate.requirePayable(tenantId, councilId, feeCode);

        Long registerId = resolveRegister(tenantId, councilId, registerCode);
        Long policyId = resolvePolicy(tenantId, councilId, policyKey);

        // Reserve, write the entry, then issue. The reservation is what makes a mid-flight failure
        // recoverable: it can be released with a reason instead of leaving an unexplained gap.
        Object[] reserved = (Object[]) entityManager
                .createNativeQuery("SELECT id, number FROM varapi.reserve_number("
                        + ":tenant, :policy, 'STUDENT', :subject, :application, :by)")
                .setParameter("tenant", tenantId)
                .setParameter("policy", policyId)
                .setParameter("subject", String.valueOf(providerId))
                .setParameter("application", applicationId)
                .setParameter("by", decidedBy)
                .getSingleResult();
        Long numberId = ((Number) reserved[0]).longValue();
        String indexNumber = String.valueOf(reserved[1]);

        Object entryId = entityManager
                .createNativeQuery("INSERT INTO varapi.provider_council_registration_records "
                        + "(tenant_id, provider_id, council_id, register_id, registration_number, "
                        + " status, effective_from, admitted_via, decided_by, registration_date) "
                        + "VALUES (:tenant, :provider, :council, :register, :number, 'ADMITTED', "
                        + " :effective, :via, :by, :effective) RETURNING id")
                .setParameter("tenant", tenantId)
                .setParameter("provider", providerId)
                .setParameter("council", councilId)
                .setParameter("register", registerId)
                .setParameter("number", indexNumber)
                .setParameter("effective", LocalDate.now())
                .setParameter("via", "application:" + applicationId)
                .setParameter("by", decidedBy)
                .getSingleResult();

        entityManager.createNativeQuery("UPDATE varapi.issued_number "
                        + "SET state = 'ISSUED', issued_at = NOW() WHERE id = :id")
                .setParameter("id", numberId)
                .executeUpdate();

        log.info("Admitted provider {} to {} as {} via application {}",
                providerId, registerCode, indexNumber, applicationId);
        return new Admission(indexNumber, ((Number) entryId).longValue(), registerCode,
                LocalDate.now());
    }

    private Long resolveRegister(UUID tenantId, Long councilId, String registerCode) {
        List<?> rows = entityManager
                .createNativeQuery("SELECT id FROM varapi.professional_registers "
                        + "WHERE tenant_id = :tenant AND council_id = :council "
                        + "AND register_code = :code")
                .setParameter("tenant", tenantId)
                .setParameter("council", councilId)
                .setParameter("code", registerCode)
                .getResultList();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This council has no register '" + registerCode + "'. Nobody is admitted to a "
                            + "register that does not exist.");
        }
        return ((Number) rows.getFirst()).longValue();
    }

    private Long resolvePolicy(UUID tenantId, Long councilId, String policyKey) {
        List<?> rows = entityManager
                .createNativeQuery("SELECT id FROM varapi.numbering_policy "
                        + "WHERE tenant_id = :tenant AND council_id = :council "
                        + "AND policy_key = :key")
                .setParameter("tenant", tenantId)
                .setParameter("council", councilId)
                .setParameter("key", policyKey)
                .getResultList();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This council has no numbering policy '" + policyKey + "'.");
        }
        return ((Number) rows.getFirst()).longValue();
    }
}
