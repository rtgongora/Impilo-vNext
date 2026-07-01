package zw.gov.mohcc.impilo.pct.core.forms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResponseEntity;

/**
 * Fallback extraction hook used until the real {@code FormExtractionService} (Wave 4) is present.
 * Registered only when no other {@link FormExtractionHook} bean exists, so the real service takes over
 * automatically once added.
 */
@Configuration
public class NoOpFormExtractionHook {

    @Bean
    @ConditionalOnMissingBean(FormExtractionHook.class)
    public FormExtractionHook defaultFormExtractionHook() {
        return (FormResponseEntity response) -> {
            // no-op: extraction wired in Wave 4
        };
    }
}
