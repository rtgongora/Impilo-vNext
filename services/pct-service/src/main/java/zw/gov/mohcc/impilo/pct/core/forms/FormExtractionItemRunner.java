package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResponseEntity;

import java.util.Map;

/**
 * Runs one form-extraction mapping item in a transaction of its own.
 *
 * <p>Same reasoning as {@link zw.gov.mohcc.impilo.pct.core.clinical.NewbornEpisodeOpener}: catching
 * a {@code RuntimeException} from an {@code @Transactional} callee is not enough. The callee has
 * already marked the surrounding transaction rollback-only, so the form response would be
 * discarded at commit even though extraction returned normally.</p>
 *
 * <p>Implemented with {@link TransactionTemplate} rather than {@code @Transactional} on this method:
 * when the item marks the transaction rollback-only and we catch the exception to record failure,
 * Spring's {@code @Transactional} interceptor would still throw {@code UnexpectedRollbackException}
 * on the way out — aborting the mapping loop so later items never run. The template completes the
 * rolled-back transaction inside {@code execute}, so the caller sees a normal {@code false} and
 * can continue.</p>
 */
@Component
public class FormExtractionItemRunner {

    private final FormExtractionFailureRecorder failureRecorder;
    private final TransactionTemplate requiresNew;

    public FormExtractionItemRunner(FormExtractionFailureRecorder failureRecorder,
                                    PlatformTransactionManager transactionManager) {
        this.failureRecorder = failureRecorder;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean runItem(FormResponseEntity r, Map<String, Object> m, String linkId, JsonNode value,
                           String resourceType, JsonNode answers, ItemWork work) {
        try {
            requiresNew.executeWithoutResult(status ->
                    work.extract(r, m, linkId, value, resourceType, answers));
            return true;
        } catch (RuntimeException e) {
            failureRecorder.recordFailure(r, m, linkId, resourceType, e.getMessage());
            return false;
        }
    }

    @FunctionalInterface
    public interface ItemWork {
        void extract(FormResponseEntity r, Map<String, Object> m, String linkId, JsonNode value,
                     String resourceType, JsonNode answers);
    }
}
