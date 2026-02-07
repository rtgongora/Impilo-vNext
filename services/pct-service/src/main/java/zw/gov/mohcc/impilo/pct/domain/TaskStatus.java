package zw.gov.mohcc.impilo.pct.domain;

/**
 * Status of a clinical or administrative task.
 *
 * <ul>
 *   <li>{@link #PENDING} — task created but not yet started</li>
 *   <li>{@link #IN_PROGRESS} — task is actively being worked on</li>
 *   <li>{@link #COMPLETED} — task finished successfully</li>
 *   <li>{@link #CANCELLED} — task was cancelled before completion</li>
 * </ul>
 */
public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
