package zw.gov.mohcc.impilo.clinical.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI clinicalPlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Clinical Knowledge Platform API")
                        .description("Internal APIs for EDLIZ-aligned knowledge, rules, assistant, prescribing, pathways, source PDF ingestion, and audit.")
                        .version("v1"))
                .tags(List.of(
                        new Tag()
                                .name("Source ingestion")
                                .description("Index national reference PDFs into source_sections (page-wise text, pdf/* paths). Operator-facing; restrict at the gateway."),
                        new Tag()
                                .name("Clinical assistant")
                                .description("Conversational EDLIZ-aligned answers with citations and traces."),
                        new Tag()
                                .name("Prescribing & pathways")
                                .description("Structured evaluation and care pathway metadata."),
                        new Tag()
                                .name("Knowledge curation")
                                .description("Human-in-the-loop review of proposed extracts; approve into clinical_knowledge_items.")));
    }
}
