package zw.gov.mohcc.impilo.governance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.governance.core.EmploymentMatchService;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;
import java.util.UUID;

/**
 * EC-number employment match API for Channel B (public workforce) onboarding
 * (IATG Wave-1, WS-D).
 *
 * <p>Internal, tenant-scoped endpoint consumed by the experience BFF. The
 * tenant is taken from the {@code X-Tenant-ID} trust header (never from the
 * body) — this controller sits on the {@code /internal/v1} prefix mandated by
 * the Wave-1 contract, outside the {@code /v1/*} TrustContextFilter mapping,
 * so the header is read explicitly and validated here.</p>
 */
@RestController
@RequestMapping("/internal/v1/workforce-governance/employment")
public class EmploymentMatchController {

    private final EmploymentMatchService employmentMatchService;

    public EmploymentMatchController(EmploymentMatchService employmentMatchService) {
        this.employmentMatchService = employmentMatchService;
    }

    public record EmploymentMatchRequest(
            @NotBlank(message = "ecNumber is required") String ecNumber,
            String healthId,
            String nationalId,
            String surname) {
    }

    @PostMapping("/match")
    public ResponseEntity<ApiResponse<Map<String, Object>>> match(
            @RequestHeader(name = "X-Tenant-ID") String tenantId,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody EmploymentMatchRequest request) {
        UUID tenant = parseTenant(tenantId);
        Map<String, Object> result = employmentMatchService.match(
                tenant, request.ecNumber(), request.healthId(), request.nationalId(), request.surname());
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId != null ? correlationId : ""));
    }

    private static UUID parseTenant(String tenantId) {
        try {
            return UUID.fromString(tenantId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid X-Tenant-ID trust header");
        }
    }
}
