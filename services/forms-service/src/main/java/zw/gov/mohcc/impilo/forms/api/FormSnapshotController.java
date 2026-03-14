package zw.gov.mohcc.impilo.forms.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.forms.api.dto.FormDefinitionResponse;
import zw.gov.mohcc.impilo.forms.core.FormDefinitionService;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
public class FormSnapshotController {

    private final FormDefinitionService formDefinitionService;

    public FormSnapshotController(FormDefinitionService formDefinitionService) {
        this.formDefinitionService = formDefinitionService;
    }

    @GetMapping("/internal/v1/snapshots/forms")
    public ResponseEntity<?> formSnapshot(@RequestParam(defaultValue = "0") int cursor,
                                            @RequestParam(defaultValue = "100") int limit,
                                            @RequestParam(name = "as_of", required = false) String asOfParam) {
        RequestContext ctx = RequestContextHolder.require();
        OffsetDateTime asOf = asOfParam != null ? OffsetDateTime.parse(asOfParam) : OffsetDateTime.now();

        List<FormDefinitionResponse> forms = formDefinitionService.listByTenant(ctx.tenantId());
        // Filter by as_of (forms updated before the cutoff)
        List<FormDefinitionResponse> filtered = forms.stream()
                .filter(f -> f.updatedAt() == null || !f.updatedAt().isAfter(asOf))
                .toList();

        int start = Math.min(cursor * limit, filtered.size());
        int end = Math.min(start + limit, filtered.size());
        List<FormDefinitionResponse> page = filtered.subList(start, end);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page);
        body.put("cursor", end < filtered.size() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("has_more", end < filtered.size());
        body.put("as_of", asOfParam != null ? asOfParam : asOf.toString());
        return ResponseEntity.ok(body);
    }
}
