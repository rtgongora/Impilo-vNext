package zw.gov.mohcc.impilo.forms.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.forms.api.dto.*;
import zw.gov.mohcc.impilo.forms.core.FormDefinitionService;
import zw.gov.mohcc.impilo.forms.core.FormPublicationService;
import zw.gov.mohcc.impilo.forms.core.FormVersionService;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/forms")
public class FormController {

    private final FormDefinitionService formDefinitionService;
    private final FormVersionService formVersionService;
    private final FormPublicationService formPublicationService;

    public FormController(FormDefinitionService formDefinitionService,
                          FormVersionService formVersionService,
                          FormPublicationService formPublicationService) {
        this.formDefinitionService = formDefinitionService;
        this.formVersionService = formVersionService;
        this.formPublicationService = formPublicationService;
    }

    @PostMapping
    public ResponseEntity<FormDefinitionResponse> createForm(
            @Valid @RequestBody CreateFormRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        FormDefinitionResponse response = formDefinitionService.create(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormDefinitionResponse> getForm(@PathVariable String id) {
        RequestContextHolder.require();
        FormDefinitionResponse response = formDefinitionService.getById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormDefinitionResponse> updateForm(
            @PathVariable String id,
            @Valid @RequestBody UpdateFormRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        FormDefinitionResponse response = formDefinitionService.update(id, request, ctx);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<FormVersionResponse> createVersion(
            @PathVariable String id,
            @Valid @RequestBody CreateVersionRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        FormVersionResponse response = formVersionService.create(id, request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<FormVersionResponse>> listVersions(@PathVariable String id) {
        RequestContextHolder.require();
        List<FormVersionResponse> versions = formVersionService.listByForm(id);
        return ResponseEntity.ok(versions);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<FormPublicationResponse> publish(@PathVariable String id) {
        RequestContext ctx = RequestContextHolder.require();
        FormPublicationResponse response = formPublicationService.publishLatest(id, ctx);
        return ResponseEntity.ok(response);
    }
}
