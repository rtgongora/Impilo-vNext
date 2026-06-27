package zw.gov.mohcc.impilo.oros.api.dto;

/**
 * Request to issue a printable order QR. Both fields are optional and default to a 7-day expiry
 * and a single claim.
 */
public record PrintableRequest(Integer expiryHours, Integer maxClaims) {}
