package zw.gov.mohcc.impilo.inventory.elmis.domain;

/**
 * Communication modes for the Inventory eLMIS adapter.
 *
 * <p>Determines how the adapter exchanges data with the external
 * eLMIS system. Configurable via {@code elmis.connector.mode}.</p>
 */
public enum ConnectorMode {

    /** Synchronous REST/HTTP calls to the eLMIS API. */
    REST,

    /** File-based exchange via CSV import/export (scheduled batch). */
    CSV,

    /** Asynchronous event-driven exchange via Kafka topics. */
    KAFKA
}
