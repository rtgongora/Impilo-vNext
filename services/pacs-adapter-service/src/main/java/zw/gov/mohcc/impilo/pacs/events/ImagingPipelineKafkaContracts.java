package zw.gov.mohcc.impilo.pacs.events;

/**
 * Kafka topic names for {@code imaging-pipeline.asyncapi.yaml}.
 */
public final class ImagingPipelineKafkaContracts {

    public static final String ORDER_CREATED = "imaging.order.created";
    public static final String STUDY_RECEIVED = "imaging.study.received";
    public static final String STUDY_CORRELATED = "imaging.study.correlated";
    public static final String STUDY_UNMATCHED = "imaging.study.unmatched";
    public static final String REPORT_AVAILABLE = "imaging.report.available";
    public static final String WRITEBACK_SUCCEEDED = "imaging.writeback.succeeded";
    public static final String WRITEBACK_FAILED = "imaging.writeback.failed";
    public static final String VIEWER_ACCESS_DENIED = "imaging.viewer.access_denied";

    private ImagingPipelineKafkaContracts() {}
}
