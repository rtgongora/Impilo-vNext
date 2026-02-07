package zw.gov.mohcc.impilo.zibo.domain;

/**
 * Status of an asynchronous validation or import job.
 *
 * <ul>
 *   <li>{@link #PENDING} -- job has been submitted and is awaiting processing</li>
 *   <li>{@link #PROCESSING} -- job is currently being executed</li>
 *   <li>{@link #COMPLETED} -- job finished successfully; results are available</li>
 *   <li>{@link #FAILED} -- job encountered an unrecoverable error</li>
 * </ul>
 */
public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
