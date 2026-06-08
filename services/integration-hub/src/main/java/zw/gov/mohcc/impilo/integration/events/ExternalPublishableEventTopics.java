package zw.gov.mohcc.impilo.integration.events;

/**
 * Kafka topic names for {@code health-os-external-publishable-events.asyncapi.yaml}.
 */
public final class ExternalPublishableEventTopics {

    public static final String PATIENT_REGISTERED = "patient.registered";
    public static final String PATIENT_UPDATED = "patient.updated";
    public static final String ENCOUNTER_STARTED = "encounter.started";
    public static final String ENCOUNTER_CLOSED = "encounter.closed";
    public static final String LAB_ORDER_CREATED = "lab.order.created";
    public static final String LAB_RESULT_RECEIVED = "lab.result.received";
    public static final String IMAGING_ORDER_CREATED = "imaging.order.created";
    public static final String IMAGING_REPORT_RECEIVED = "imaging.report.received";
    public static final String STOCK_LEVEL_UPDATED = "stock.level.updated";
    public static final String STOCK_REQUISITION_CREATED = "stock.requisition.created";
    public static final String PAYMENT_INITIATED = "payment.initiated";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String CLAIM_SUBMITTED = "claim.submitted";
    public static final String CLAIM_STATUS_UPDATED = "claim.status.updated";
    public static final String TELECONSULTATION_SCHEDULED = "teleconsultation.scheduled";
    public static final String TELECONSULTATION_COMPLETED = "teleconsultation.completed";
    public static final String MESSAGE_DELIVERY_UPDATED = "message.delivery.updated";
    public static final String CONSENT_GRANTED = "consent.granted";
    public static final String CONSENT_REVOKED = "consent.revoked";
    public static final String PROVIDER_VERIFIED = "provider.verified";
    public static final String FACILITY_UPDATED = "facility.updated";

    public static final String[] ALL = {
            PATIENT_REGISTERED,
            PATIENT_UPDATED,
            ENCOUNTER_STARTED,
            ENCOUNTER_CLOSED,
            LAB_ORDER_CREATED,
            LAB_RESULT_RECEIVED,
            IMAGING_ORDER_CREATED,
            IMAGING_REPORT_RECEIVED,
            STOCK_LEVEL_UPDATED,
            STOCK_REQUISITION_CREATED,
            PAYMENT_INITIATED,
            PAYMENT_COMPLETED,
            CLAIM_SUBMITTED,
            CLAIM_STATUS_UPDATED,
            TELECONSULTATION_SCHEDULED,
            TELECONSULTATION_COMPLETED,
            MESSAGE_DELIVERY_UPDATED,
            CONSENT_GRANTED,
            CONSENT_REVOKED,
            PROVIDER_VERIFIED,
            FACILITY_UPDATED
    };

    private ExternalPublishableEventTopics() {}
}
