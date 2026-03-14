package zw.gov.mohcc.impilo.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TemplateVersionRequest(

        @NotBlank
        String content,

        @Size(max = 512)
        String subject,

        @Size(max = 1024)
        String changelog
) {
}
