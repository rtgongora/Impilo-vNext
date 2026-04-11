package zw.gov.mohcc.impilo.clinical.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI clinicalPlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Clinical Knowledge Platform API")
                        .description("Internal APIs for EDLIZ-aligned knowledge, rules, assistant, prescribing, pathways, and audit.")
                        .version("v1"));
    }
}
