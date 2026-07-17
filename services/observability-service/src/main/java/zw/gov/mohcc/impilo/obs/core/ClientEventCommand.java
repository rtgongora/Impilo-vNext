package zw.gov.mohcc.impilo.obs.core;

/**
 * PII-free client-event ingest command.
 *
 * <p>Allow-listed fields only. There is deliberately no free-text message or
 * body field: this sink stores telemetry breadcrumbs (route + code + trace
 * ids + status), never user content.</p>
 *
 * @param route         the client route that emitted the event
 * @param code          a short event/error code
 * @param correlationId optional trace correlation id
 * @param requestId     optional trace request id
 * @param status        optional HTTP status code observed by the client
 * @param at            optional ISO-8601 occurrence timestamp (parsed defensively)
 */
public record ClientEventCommand(
        String route,
        String code,
        String correlationId,
        String requestId,
        Integer status,
        String at) {
}
