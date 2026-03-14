package zw.gov.mohcc.impilo.integration.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;
import zw.gov.mohcc.impilo.integration.api.dto.MappingTemplateRequest;
import zw.gov.mohcc.impilo.integration.api.dto.MappingTemplateResponse;
import zw.gov.mohcc.impilo.integration.service.MappingTemplateService;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/mapping-templates")
public class MappingTemplateController {

    private final MappingTemplateService mappingTemplateService;

    public MappingTemplateController(MappingTemplateService mappingTemplateService) {
        this.mappingTemplateService = mappingTemplateService;
    }

    @PostMapping
    public ResponseEntity<MappingTemplateResponse> createMappingTemplate(
            @Valid @RequestBody MappingTemplateRequest request) {
        FederationAuthority.requireNational();
        RequestContext ctx = RequestContextHolder.require();

        MappingTemplateResponse response = mappingTemplateService.createTemplate(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MappingTemplateResponse>> listMappingTemplates() {
        RequestContext ctx = RequestContextHolder.require();

        List<MappingTemplateResponse> templates = mappingTemplateService.listTemplates(ctx.tenantId());
        return ResponseEntity.ok(templates);
    }
}
