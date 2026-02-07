package zw.gov.mohcc.impilo.zibo.api.dto;

import java.util.List;

/**
 * DTO representing the result of a terminology validation operation.
 *
 * @param valid  true if the validation passed (no ERROR-level issues)
 * @param issues the list of validation issues found
 */
public record ValidationResultDto(
        boolean valid,
        List<ValidationIssueDto> issues
) {}
