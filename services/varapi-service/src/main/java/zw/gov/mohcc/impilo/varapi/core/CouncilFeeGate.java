package zw.gov.mohcc.impilo.varapi.core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The fee gate (NCZ-W1C). Decides whether a council fee is payable, and refuses to guess.
 *
 * <p>The failure this exists to prevent is specific and quiet. A fee whose amount the Council has
 * not yet set is NULL in the schedule. Read carelessly, NULL becomes zero, zero becomes "nothing to
 * pay", and an applicant sails through a gate that was supposed to stop them — with no error
 * anywhere, because every line of code did what it was written to do.</p>
 *
 * <p>So there are three verdicts and no default. {@code NOT_CONFIGURED} is not a variety of
 * {@code PAYABLE} with a missing number, and it is not {@code NO_SUCH_FEE}: it means the Council
 * owes a decision, the platform knows which one, and the charge cannot proceed until it is made.
 * NCZ-DEC-002 says an unconfigured fee must surface rather than silently skip the gate; this class
 * is where that stops being a sentence in a document.</p>
 */
@Component
public class CouncilFeeGate {

    private static final Logger log = LoggerFactory.getLogger(CouncilFeeGate.class);

    public enum Verdict {
        /** The Council has set this fee and it must be settled before the gated act proceeds. */
        PAYABLE,
        /** The fee exists but its value is not set. The gated act does NOT proceed. */
        NOT_CONFIGURED,
        /** No such fee is defined for this council. The gated act does NOT proceed. */
        NO_SUCH_FEE
    }

    /**
     * @param decisionRef the council decision the value is waiting on, when NOT_CONFIGURED — so the
     *                    applicant is told what is outstanding rather than that something broke.
     */
    public record FeeVerdict(Verdict verdict, BigDecimal amount, String currency,
                             String decisionRef) {

        /**
         * The only question callers should ask. Deliberately NOT `verdict == PAYABLE || amount ==
         * null` or any variant that treats an absent value as permission: an unset fee blocks.
         */
        public boolean allowsChargeToProceed() {
            return verdict == Verdict.PAYABLE;
        }

        /** What to tell the applicant. Never "free", never a blank. */
        public String explain() {
            return switch (verdict) {
                case PAYABLE -> "Payable: " + amount + " " + currency;
                case NOT_CONFIGURED -> "This fee has not been set by the Council yet"
                        + (decisionRef == null ? "." : " (" + decisionRef + ").")
                        + " Your application cannot be invoiced until it is.";
                case NO_SUCH_FEE -> "No such fee is defined for this council.";
            };
        }
    }

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Ask the schedule. The verdict is computed in SQL (varapi V042's {@code fee_verdict}) so that
     * every caller — Java, a report, a future service — reads the same rule rather than each
     * re-deriving what a NULL amount means.
     */
    @Transactional(readOnly = true)
    public FeeVerdict verdictFor(UUID tenantId, Long councilId, String feeCode) {
        List<?> rows = entityManager
                .createNativeQuery("SELECT verdict, amount, currency, decision_ref "
                        + "FROM varapi.fee_verdict(:tenant, :council, :code)")
                .setParameter("tenant", tenantId)
                .setParameter("council", councilId)
                .setParameter("code", feeCode)
                .getResultList();

        if (rows.isEmpty()) {
            // The function always returns a row, so an empty result means something is wrong with
            // the query rather than with the fee. Fail closed: never read absence as permission.
            log.error("Fee verdict query returned nothing for council {} fee {} — blocking",
                    councilId, feeCode);
            return new FeeVerdict(Verdict.NO_SUCH_FEE, null, null, null);
        }

        Object[] row = (Object[]) rows.getFirst();
        String verdict = row[0] == null ? "NO_SUCH_FEE" : row[0].toString();
        return new FeeVerdict(
                Verdict.valueOf(verdict),
                (BigDecimal) row[1],
                row[2] == null ? null : row[2].toString().trim(),
                row[3] == null ? null : row[3].toString());
    }

    /**
     * The gate itself. Throws unless the fee is genuinely payable.
     *
     * <p>Written as a refusal rather than a boolean because a boolean invites {@code if (!blocked)}
     * and a future edit that inverts it. There is one way past this method.</p>
     */
    @Transactional(readOnly = true)
    public FeeVerdict requirePayable(UUID tenantId, Long councilId, String feeCode) {
        FeeVerdict verdict = verdictFor(tenantId, councilId, feeCode);
        if (!verdict.allowsChargeToProceed()) {
            throw new FeeNotChargeableException(feeCode, verdict);
        }
        return verdict;
    }

    /** Raised when a gated act was attempted against a fee that cannot be charged. */
    public static class FeeNotChargeableException extends RuntimeException {
        private final transient FeeVerdict verdict;

        public FeeNotChargeableException(String feeCode, FeeVerdict verdict) {
            super("Fee " + feeCode + " cannot be charged. " + verdict.explain());
            this.verdict = verdict;
        }

        public FeeVerdict getVerdict() {
            return verdict;
        }
    }
}
