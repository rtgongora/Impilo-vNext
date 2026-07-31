package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.security.RecoveryExecutionService;

@RestController
@RequestMapping("/internal/v1/settings/security/recovery-cases")
public class LostDeviceRecoveryBffController {
    private final TshepoIdentityServiceClient identity; private final RecoveryExecutionService executor;
    public LostDeviceRecoveryBffController(TshepoIdentityServiceClient identity,RecoveryExecutionService executor){this.identity=identity;this.executor=executor;}
    @PostMapping public JsonNode create(@RequestHeader("X-Tenant-ID") String tenant,@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,Object> body){requirePrivilegedAal(jwt);return identity.createRecoveryCase(tenant,actor(jwt),body);}
    @GetMapping("/{id}") public JsonNode get(@RequestHeader("X-Tenant-ID") String tenant,@PathVariable UUID id){return identity.getRecoveryCase(tenant,id);}
    @PostMapping("/{id}/approval") public JsonNode approve(@RequestHeader("X-Tenant-ID") String tenant,@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestBody Map<String,Object> body){requirePrivilegedAal(jwt);JsonNode approved=identity.approveRecoveryCase(tenant,actor(jwt),id,body);return body.get("approved") instanceof Boolean value&&value ? executor.execute(tenant,id,approved):approved;}
    private static String actor(Jwt jwt){if(jwt==null||jwt.getSubject()==null||jwt.getSubject().isBlank())throw new IllegalArgumentException("Authenticated actor is required");return jwt.getSubject();}
    private static void requirePrivilegedAal(Jwt jwt){
        if(jwt==null||!"urn:impilo:aal3".equals(jwt.getClaimAsString("acr"))){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"STEP_UP_AAL3_REQUIRED");
        }
    }
}
