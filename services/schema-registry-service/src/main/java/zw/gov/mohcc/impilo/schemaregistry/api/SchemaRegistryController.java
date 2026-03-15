package zw.gov.mohcc.impilo.schemaregistry.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.contract.schema.CompatibilityResult;
import zw.gov.mohcc.impilo.schemaregistry.api.dto.RegisterSchemaRequest;
import zw.gov.mohcc.impilo.schemaregistry.core.SchemaRegistryService;
import zw.gov.mohcc.impilo.schemaregistry.domain.SchemaVersionEntity;
import zw.gov.mohcc.impilo.schemaregistry.domain.SubjectEntity;

import java.util.*;

@RestController
public class SchemaRegistryController {

    private final SchemaRegistryService registryService;

    public SchemaRegistryController(SchemaRegistryService registryService) {
        this.registryService = registryService;
    }

    @PostMapping("/internal/v1/schemas")
    public ResponseEntity<?> registerSchema(@Valid @RequestBody RegisterSchemaRequest request,
                                             jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        Map<String, Object> result = registryService.registerSchema(
                UUID.fromString(ctx.tenantId()), ctx.correlationId(), idempotencyKey, request);

        if (result.containsKey("compatible") && Boolean.FALSE.equals(result.get("compatible"))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/internal/v1/schemas/subjects")
    public ResponseEntity<?> listSubjects() {
        RequestContextHolder.require();
        List<SubjectEntity> subjects = registryService.listSubjects();
        List<Map<String, Object>> items = subjects.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("subject", s.getSubjectName());
            m.put("compatibility", s.getCompatibility());
            m.put("description", s.getDescription());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("items", items, "count", items.size()));
    }

    @GetMapping("/internal/v1/schemas/subjects/{subject}")
    public ResponseEntity<?> getSubject(@PathVariable("subject") String subjectName) {
        RequestContext ctx = RequestContextHolder.require();
        return registryService.getSubject(subjectName)
                .map(s -> {
                    var versions = registryService.listVersions(s.getId());
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("subject", s.getSubjectName());
                    result.put("compatibility", s.getCompatibility());
                    result.put("versions", versions.stream().map(v -> Map.of(
                            "version", v.getVersion(),
                            "fingerprint", v.getFingerprint(),
                            "created_at", v.getCreatedAt().toString()
                    )).toList());
                    return ResponseEntity.ok((Object) result);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Subject not found: " + subjectName,
                                ctx.requestId(), ctx.correlationId())));
    }

    @GetMapping("/internal/v1/schemas/subjects/{subject}/versions/{version}")
    public ResponseEntity<?> getVersion(@PathVariable("subject") String subjectName,
                                         @PathVariable("version") int version) {
        RequestContext ctx = RequestContextHolder.require();
        return registryService.getSubject(subjectName)
                .flatMap(s -> registryService.getVersion(s.getId(), version))
                .map(v -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("subject", subjectName);
                    result.put("version", v.getVersion());
                    result.put("schema", v.getSchemaJson());
                    result.put("fingerprint", v.getFingerprint());
                    return ResponseEntity.ok((Object) result);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Schema version not found",
                                ctx.requestId(), ctx.correlationId())));
    }

    @PostMapping("/internal/v1/schemas/compatibility")
    public ResponseEntity<?> checkCompatibility(@RequestBody Map<String, String> request) {
        RequestContextHolder.require();
        String subject = request.get("subject");
        String schema = request.get("schema");

        CompatibilityResult result = registryService.checkCompatibility(subject, schema);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("compatible", result.compatible());
        response.put("violations", result.violations());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/internal/v1/schemas/catalog")
    public ResponseEntity<?> catalog() {
        RequestContextHolder.require();
        List<SubjectEntity> subjects = registryService.listSubjects();
        List<Map<String, Object>> items = subjects.stream().map(s -> {
            var latest = registryService.getLatestVersion(s.getId());
            var allVersions = registryService.listVersions(s.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("subject", s.getSubjectName());
            m.put("compatibility", s.getCompatibility());
            m.put("description", s.getDescription());
            m.put("version_count", allVersions.size());
            latest.ifPresent(v -> {
                m.put("latest_version", v.getVersion());
                m.put("latest_fingerprint", v.getFingerprint());
                m.put("latest_created_at", v.getCreatedAt().toString());
            });
            // Derive type from subject name convention: impilo.{service}.{entity}.{action}.v{N}
            String name = s.getSubjectName();
            if (name.contains(".")) {
                String[] parts = name.split("\\.");
                m.put("service", parts.length > 1 ? parts[1] : "unknown");
                m.put("entity", parts.length > 2 ? parts[2] : "unknown");
                m.put("action", parts.length > 3 ? parts[3] : "unknown");
            }
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of(
                "items", items,
                "count", items.size(),
                "catalog_type", "event_schema",
                "generated_at", java.time.OffsetDateTime.now().toString()
        ));
    }
}
