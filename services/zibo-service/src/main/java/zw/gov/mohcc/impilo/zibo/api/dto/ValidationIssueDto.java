package zw.gov.mohcc.impilo.zibo.api.dto;

/**
 * DTO representing a single validation issue.
 *
 * @param severity     the severity level (ERROR, WARNING, INFORMATION)
 * @param code         a machine-readable issue code
 * @param message      a human-readable description
 * @param canonicalUrl the canonical URL of the relevant CodeSystem
 */
public record ValidationIssueDto(
        String severity,
        String code,
        String message,
        String canonicalUrl
) {}
