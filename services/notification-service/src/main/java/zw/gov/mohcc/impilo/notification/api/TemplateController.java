package zw.gov.mohcc.impilo.notification.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;
import zw.gov.mohcc.impilo.notification.api.dto.TemplateRequest;
import zw.gov.mohcc.impilo.notification.api.dto.TemplateResponse;
import zw.gov.mohcc.impilo.notification.api.dto.TemplateVersionRequest;
import zw.gov.mohcc.impilo.notification.api.dto.TemplateVersionResponse;
import zw.gov.mohcc.impilo.notification.service.TemplateService;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> createTemplate(@Valid @RequestBody TemplateRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        FederationAuthority.requireNational();

        TemplateResponse response = templateService.createTemplate(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{key}")
    public ResponseEntity<TemplateResponse> getByKey(@PathVariable String key) {
        RequestContextHolder.require();

        return templateService.getByKey(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> listTemplates() {
        RequestContext ctx = RequestContextHolder.require();

        List<TemplateResponse> templates = templateService.listTemplates(ctx.tenantId());
        return ResponseEntity.ok(templates);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<TemplateResponse> publishTemplate(@PathVariable String id) {
        RequestContext ctx = RequestContextHolder.require();
        FederationAuthority.requireNational();

        TemplateResponse response = templateService.publishTemplate(id, ctx);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<TemplateResponse> retireTemplate(@PathVariable String id) {
        RequestContext ctx = RequestContextHolder.require();
        FederationAuthority.requireNational();

        TemplateResponse response = templateService.retireTemplate(id, ctx);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<TemplateVersionResponse> createVersion(
            @PathVariable String id,
            @Valid @RequestBody TemplateVersionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        FederationAuthority.requireNational();

        TemplateVersionResponse response = templateService.createVersion(id, request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<TemplateVersionResponse>> listVersions(@PathVariable String id) {
        RequestContextHolder.require();

        List<TemplateVersionResponse> versions = templateService.listVersions(id);
        return ResponseEntity.ok(versions);
    }
}
