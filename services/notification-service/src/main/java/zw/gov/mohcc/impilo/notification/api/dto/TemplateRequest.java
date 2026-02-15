package zw.gov.mohcc.impilo.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TemplateRequest(

        @NotBlank
        @Size(max = 128)
        String key,

        @NotBlank
        @Size(max = 32)
        String channel,

        @Size(max = 512)
        String subject,

        @NotBlank
        String body,

        Boolean enabled
) {
    public boolean isEnabledOrDefault() {
        return enabled == null || enabled;
    }
}
