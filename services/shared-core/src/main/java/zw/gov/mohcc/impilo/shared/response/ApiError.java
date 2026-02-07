package zw.gov.mohcc.impilo.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standardized error payload within {@link ApiResponse}.
 *
 * Error codes are prefixed by domain (e.g., TSHEPO_INVALID_PURPOSE, VITO_ALIAS_NOT_FOUND).
 * Messages MUST NOT leak whether an identity exists (anti-enumeration).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        int status
) {
    // Common error codes shared across all services
    public static final String MISSING_HEADERS = "MISSING_HEADERS";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
}
