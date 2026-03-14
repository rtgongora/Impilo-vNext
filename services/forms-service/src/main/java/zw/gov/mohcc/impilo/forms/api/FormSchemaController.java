package zw.gov.mohcc.impilo.forms.api;

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
import zw.gov.mohcc.impilo.forms.api.dto.FormSchemaRequest;
import zw.gov.mohcc.impilo.forms.api.dto.FormSchemaResponse;
import zw.gov.mohcc.impilo.forms.api.dto.FormVersionRequest;
import zw.gov.mohcc.impilo.forms.api.dto.FormVersionResponse;
import zw.gov.mohcc.impilo.forms.api.dto.ValidationRequest;
import zw.gov.mohcc.impilo.forms.api.dto.ValidationResponse;
import zw.gov.mohcc.impilo.forms.service.FormSchemaService;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/forms")
public class FormSchemaController {

    private final FormSchemaService formSchemaService;

    public FormSchemaController(FormSchemaService formSchemaService) {
        this.formSchemaService = formSchemaService;
    }

    @PostMapping
    public ResponseEntity<FormSchemaResponse> createSchema(@Valid @RequestBody FormSchemaRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        FormSchemaResponse response = formSchemaService.createSchema(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<FormSchemaResponse>> listSchemas() {
        RequestContext ctx = RequestContextHolder.require();
        List<FormSchemaResponse> schemas = formSchemaService.listSchemas(ctx.tenantId());
        return ResponseEntity.ok(schemas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormSchemaResponse> getSchema(@PathVariable String id) {
        RequestContext ctx = RequestContextHolder.require();
        return formSchemaService.getSchema(id, ctx.tenantId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<FormVersionResponse> createVersion(
            @PathVariable String id,
            @Valid @RequestBody FormVersionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        FormVersionResponse response = formSchemaService.createVersion(id, request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<FormSchemaResponse> publishSchema(@PathVariable String id) {
        RequestContext ctx = RequestContextHolder.require();
        FormSchemaResponse response = formSchemaService.publishSchema(id, ctx);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<FormSchemaResponse> retireSchema(@PathVariable String id) {
        RequestContext ctx = RequestContextHolder.require();
        FormSchemaResponse response = formSchemaService.retireSchema(id, ctx);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<ValidationResponse> validatePayload(
            @PathVariable String id,
            @Valid @RequestBody ValidationRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        ValidationResponse response = formSchemaService.validatePayload(id, request, ctx.tenantId());
        return ResponseEntity.ok(response);
    }
}
