package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.ShadowDecisionLogEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.ShadowDecisionLogRepository;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Persists shadow measurements, and is incapable of breaking anything if it cannot.
 *
 * <p><b>{@code REQUIRES_NEW} is load-bearing.</b> Without it this write would join whatever
 * transaction is in scope, and a constraint violation or a full disk would mark that transaction
 * rollback-only — letting a measurement failure destroy real work. Its own transaction fails
 * alone.</p>
 *
 * <p><b>Failure is counted, not swallowed.</b> A probe that silently disappears is worse than one
 * that errors: it makes the dataset look complete while under-reporting exactly the requests that
 * were hardest to evaluate. {@link #droppedCount()} is the number of measurements this process
 * knows it lost, and it belongs in the evidence report.</p>
 */
@Component
public class ShadowDecisionRecorder {

    private static final Logger log = LoggerFactory.getLogger(ShadowDecisionRecorder.class);

    private final ShadowDecisionLogRepository repository;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong recorded = new AtomicLong();

    public ShadowDecisionRecorder(ShadowDecisionLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ShadowDecisionLogEntity row) {
        try {
            repository.save(row);
            recorded.incrementAndGet();
        } catch (RuntimeException e) {
            // Deliberately terminal. Nothing above this may learn that persistence failed, because
            // the only caller is a measurement path and the alternative is a database outage
            // becoming an authorization outage.
            dropped.incrementAndGet();
            log.warn("shadow decision not recorded ({} lost so far): {}",
                    dropped.get(), e.getClass().getSimpleName());
        }
    }

    /** Measurements this process knows it lost. Report alongside the dataset, never instead of it. */
    public long droppedCount() { return dropped.get(); }

    public long recordedCount() { return recorded.get(); }
}
