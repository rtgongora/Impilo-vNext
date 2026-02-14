package zw.gov.mohcc.impilo.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TemplateRequest(

        @NotBlank
        @Size(max = 32)
        String channel,

        @NotBlank
        @Size(max = 256)
        String name,

        @NotBlank
        String content,

        Boolean enabled
) {
    /**
     * Returns enabled value, defaulting to true if null.
     */
    public boolean isEnabledOrDefault() {
        return enabled == null || enabled;
    }
}
