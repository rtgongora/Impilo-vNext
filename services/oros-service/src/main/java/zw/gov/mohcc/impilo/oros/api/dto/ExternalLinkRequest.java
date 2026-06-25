package zw.gov.mohcc.impilo.oros.api.dto;

/**
 * Request to issue a secure external result link. Both fields are optional and default to a
 * 72-hour expiry and 3 claims.
 */
public record ExternalLinkRequest(Integer expiryHours, Integer maxClaims) {}
