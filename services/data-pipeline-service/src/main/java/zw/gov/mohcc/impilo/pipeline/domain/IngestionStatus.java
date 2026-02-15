package zw.gov.mohcc.impilo.pipeline.domain;

/**
 * Status of an ingested event in the pipeline.
 */
public enum IngestionStatus {
    ACCEPTED,
    REJECTED,
    DUPLICATE
}
