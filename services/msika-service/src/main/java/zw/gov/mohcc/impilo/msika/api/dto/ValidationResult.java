package zw.gov.mohcc.impilo.msika.api.dto;

import java.util.List;

public record ValidationResult(
    boolean valid,
    List<ValidationIssue> issues
) {
    public record ValidationIssue(
        String severity,
        String code,
        String message,
        String path
    ) {}
}
