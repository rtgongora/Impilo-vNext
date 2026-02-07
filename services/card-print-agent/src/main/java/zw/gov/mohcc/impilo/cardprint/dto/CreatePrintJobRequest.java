package zw.gov.mohcc.impilo.cardprint.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request payload for creating a new print job.
 *
 * @param jobType       the type of card/document to print (PROVIDER_CARD, CLIENT_CARD, etc.)
 * @param subjectType   the type of subject (PRACTITIONER, CLIENT, FACILITY)
 * @param subjectId     the identifier of the subject
 * @param subjectName   human-readable name for display
 * @param templateName  the template to use for rendering
 * @param payload       template data as key-value pairs
 * @param priority      job priority (1=lowest, 10=highest, default 5)
 */
public record CreatePrintJobRequest(
        @NotBlank(message = "jobType is required")
        @Size(max = 50)
        String jobType,

        @NotBlank(message = "subjectType is required")
        @Size(max = 50)
        String subjectType,

        @NotBlank(message = "subjectId is required")
        @Size(max = 255)
        String subjectId,

        @Size(max = 500)
        String subjectName,

        @NotBlank(message = "templateName is required")
        @Size(max = 100)
        String templateName,

        @NotNull(message = "payload is required")
        Map<String, Object> payload,

        @Min(value = 1, message = "priority must be between 1 and 10")
        @Max(value = 10, message = "priority must be between 1 and 10")
        Integer priority
) {
    public CreatePrintJobRequest {
        if (priority == null) {
            priority = 5;
        }
    }
}
