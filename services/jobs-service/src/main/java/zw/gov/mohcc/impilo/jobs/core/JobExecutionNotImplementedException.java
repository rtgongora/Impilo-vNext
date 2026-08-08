package zw.gov.mohcc.impilo.jobs.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a job trigger is requested. There is <b>no executor</b>: this service has no
 * scheduler, no worker, no Kafka producer and no consumer, and nothing anywhere in the estate
 * subscribes to {@code JOB_TRIGGERED}.
 *
 * <p>The previous behaviour persisted a {@link zw.gov.mohcc.impilo.jobs.domain.JobStatus#PENDING}
 * execution row, appended an outbox event and returned {@code 201 Created}. That row stays PENDING
 * for ever: 201 asserts the run was accepted and will happen, and nothing was ever going to make
 * it happen. Scheduled work in this estate is run by per-service {@code @Scheduled} methods, not
 * by this service.</p>
 *
 * <p>Refusing keeps the gap visible. Returning 201 hid it behind a status that only a working
 * executor could ever clear.</p>
 */
@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class JobExecutionNotImplementedException extends RuntimeException {

    public JobExecutionNotImplementedException(Long jobDefinitionId) {
        super("Job definition " + jobDefinitionId + " was NOT triggered: this service has no "
                + "executor, so no execution can run. No execution row has been created.");
    }
}
