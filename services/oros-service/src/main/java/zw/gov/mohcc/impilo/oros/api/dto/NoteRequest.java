package zw.gov.mohcc.impilo.oros.api.dto;

/**
 * Minimal optional-note request body for lifecycle actions that need no additional input
 * beyond an audit note (e.g. arrive, release).
 */
public record NoteRequest(String note) {}
