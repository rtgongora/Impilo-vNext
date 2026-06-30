package zw.gov.mohcc.impilo.inventory.api.dto;

import java.util.UUID;

/**
 * Result of a PCT consumption: the Dura ledger event created, and whether a
 * prior reservation was fulfilled.
 */
public record PctConsumeResponse(
        UUID ledgerEventId,
        boolean reservationConsumed
) {}
