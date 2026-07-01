package zw.gov.mohcc.impilo.forms.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.forms.api.dto.ClinicalFormUpsertRequest;
import zw.gov.mohcc.impilo.forms.service.FormSchemaService;

import java.io.InputStream;
import java.util.UUID;

/**
 * Seeds the WHO-DAK exemplar clinical encounter form definitions from classpath {@code seed-forms/*.json}
 * on startup. Idempotent (upsert by tenant+formKey). Gated by {@code forms.seed.clinical-forms.enabled}
 * (default true) so it runs in dev/preview and CI but can be disabled in production if content is governed
 * externally. Each seed file is a {@link ClinicalFormUpsertRequest} shape whose {@code definition} is an
 * embedded object (stringified into the immutable version snapshot).
 */
@Component
public class ClinicalFormSeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClinicalFormSeedLoader.class);

    private final FormSchemaService formSchemaService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String tenantId;
    private final String podId;

    public ClinicalFormSeedLoader(FormSchemaService formSchemaService,
                                  ObjectMapper objectMapper,
                                  @Value("${forms.seed.clinical-forms.enabled:true}") boolean enabled,
                                  @Value("${forms.seed.clinical-forms.tenant-id:00000000-0000-4000-8000-000000000001}") String tenantId,
                                  @Value("${forms.seed.clinical-forms.pod-id:national-spine}") String podId) {
        this.formSchemaService = formSchemaService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.tenantId = tenantId;
        this.podId = podId;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Clinical form seeding disabled (forms.seed.clinical-forms.enabled=false)");
            return;
        }
        RequestContext ctx = RequestContext.of(tenantId, podId,
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), null, null, null);
        int loaded = 0;
        int failed = 0;
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:seed-forms/*.json");
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    JsonNode root = objectMapper.readTree(in);
                    if (root instanceof ObjectNode obj && obj.has("definition") && obj.get("definition").isObject()) {
                        // Stringify the embedded DAK definition into the version snapshot column.
                        obj.put("definition", obj.get("definition").toString());
                    }
                    ClinicalFormUpsertRequest req = objectMapper.treeToValue(root, ClinicalFormUpsertRequest.class);
                    formSchemaService.upsertClinicalForm(req, ctx);
                    loaded++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Failed to seed clinical form from {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Clinical form seed scan failed: {}", e.getMessage());
        }
        log.info("Clinical form seeding complete: {} loaded, {} failed (tenant={})", loaded, failed, tenantId);
    }
}
