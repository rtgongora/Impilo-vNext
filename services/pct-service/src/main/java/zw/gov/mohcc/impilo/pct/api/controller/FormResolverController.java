package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.forms.FormResolution;
import zw.gov.mohcc.impilo.pct.core.forms.FormResolveInput;
import zw.gov.mohcc.impilo.pct.core.forms.FormResolverService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

/**
 * Resolves the patient/cadre/setting-aware structured form set for an encounter.
 * The resolver composes the Cadre Engine (GAP-4) and audits every resolution.
 */
@RestController
@RequestMapping("/v1/forms")
public class FormResolverController {

    private final FormResolverService resolverService;

    public FormResolverController(FormResolverService resolverService) {
        this.resolverService = resolverService;
    }

    @PostMapping("/resolve")
    public ResponseEntity<ApiResponse<FormResolution>> resolve(@RequestBody ResolveFormsRequest body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        FormResolveInput input = new FormResolveInput(
                body.encounterId(), body.cpid(), body.role(), body.cadre(), body.scope(),
                body.visitType(), body.acuity(), body.context(), body.accessState(),
                body.careSetting(), body.careStage(), body.specialty(),
                body.ageMonths(), body.sex(), body.pregnant(), body.programmes(), body.conditionCodes());
        FormResolution resolution = resolverService.resolve(input);
        return ResponseEntity.ok(ApiResponse.ok(resolution, correlationId));
    }

    /** Request body for form resolution. */
    public record ResolveFormsRequest(
            Long encounterId,
            String cpid,
            String role,
            String cadre,
            String scope,
            String visitType,
            Integer acuity,
            String context,
            String accessState,
            String careSetting,
            String careStage,
            String specialty,
            Integer ageMonths,
            String sex,
            Boolean pregnant,
            List<String> programmes,
            List<String> conditionCodes) {
    }
}
