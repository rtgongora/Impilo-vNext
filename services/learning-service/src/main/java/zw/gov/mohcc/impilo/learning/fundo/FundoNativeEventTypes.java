package zw.gov.mohcc.impilo.learning.fundo;

/**
 * V1.1-compliant event type names for native Impilo Fundo LMS actions
 * introduced in Phase 5D.
 *
 * <p>These event types are emitted directly into {@code lrn_event_outbox}
 * via {@link FundoOutboxAppender}. Unlike the legacy event types tracked in
 * {@code RepoEventTypeContractTest.legacyEventTypes()}, the Phase 5D event
 * types are <strong>natively v1.1-compliant from inception</strong> — there
 * is no legacy counterpart to dual-emit because the underlying domain
 * concepts (course/module/lesson/enrolment/assessment/certificate) did not
 * exist before Phase 5.</p>
 *
 * <p>Convention enforced by {@code EventEnvelopeValidator} in
 * {@code libs/contract-tests}: {@code impilo.{service}.{entity}.{action}.v{N}}.</p>
 */
public final class FundoNativeEventTypes {

    private FundoNativeEventTypes() {}

    public static final String COURSE_PUBLISHED                = "impilo.learning.course.published.v1";
    public static final String ENROLMENT_CREATED               = "impilo.learning.enrolment.created.v1";
    public static final String ENROLMENT_CANCELLED             = "impilo.learning.enrolment.cancelled.v1";
    public static final String LESSON_OPENED                   = "impilo.learning.lesson.opened.v1";
    public static final String PROGRESS_STARTED                = "impilo.learning.progress.started.v1";
    public static final String PROGRESS_COMPLETED              = "impilo.learning.progress.completed.v1";
    public static final String ASSESSMENT_ATTEMPT_SUBMITTED    = "impilo.learning.assessment.attempt.submitted.v1";
    public static final String ASSESSMENT_ATTEMPT_REVIEWED     = "impilo.learning.assessment.attempt.reviewed.v1";
    public static final String CERTIFICATE_ISSUED             = "impilo.learning.certificate.issued.v1";
    public static final String COURSE_COMPLETED               = "impilo.learning.course.completed.v1";
    public static final String CERTIFICATE_REFRESHER_DUE      = "impilo.learning.certificate.refresher.due.v1";
    public static final String CERTIFICATE_EXPIRED            = "impilo.learning.certificate.expired.v1";
    public static final String ASSIGNMENT_SUBMITTED           = "impilo.learning.assignment.submitted.v1";
    public static final String ASSIGNMENT_MARKED              = "impilo.learning.assignment.marked.v1";
    public static final String PROVIDER_ACCREDITED            = "impilo.learning.provider.accredited.v1";
}
