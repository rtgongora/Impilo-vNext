package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoCertificateService;

/**
 * Native Fundo v1.1 certificate read surface (Phase 5B). Issuance lives on
 * the enrolment controller ({@code POST /enrolments/{id}/certificate}); this
 * controller is read-only.
 */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoCertificateController {

    private final FundoCertificateService certificateService;

    public FundoCertificateController(FundoCertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping("/certificates")
    public ResponseEntity<Map<String, Object>> listCertificates(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subjectType", subjectType);
        data.put("subjectId", subjectId);
        if (tenantId == null || subjectType == null || subjectId == null) {
            data.put("items", List.of());
            return FundoV11Support.dataEnvelope(data);
        }
        data.put("items", certificateService.listForSubject(tenantId, subjectType, subjectId));
        return FundoV11Support.dataEnvelope(data);
    }

    @GetMapping("/certificates/{certificateId}")
    public ResponseEntity<Map<String, Object>> getCertificate(@PathVariable String certificateId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID cid = FundoV11Support.tryParseUuid(certificateId);
        if (tenantId == null || cid == null) {
            return FundoV11Support.notFound("CERTIFICATE_NOT_FOUND", "Certificate not found");
        }
        return certificateService.get(tenantId, cid)
                .map(v -> FundoV11Support.dataEnvelope("certificate", v))
                .orElseGet(() -> FundoV11Support.notFound("CERTIFICATE_NOT_FOUND", "Certificate not found"));
    }
}
