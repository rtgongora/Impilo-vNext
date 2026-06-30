package zw.gov.mohcc.impilo.inventory.api.dto;

/**
 * Request to close a recall with an outcome note.
 */
public record CloseRecallRequest(
        String closureNote
) {}
