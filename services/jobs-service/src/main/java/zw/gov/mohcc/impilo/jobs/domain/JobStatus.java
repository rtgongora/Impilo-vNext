package zw.gov.mohcc.impilo.jobs.domain;

/**
 * Status of a job execution.
 */
public enum JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
