package zw.gov.mohcc.impilo.vito.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.api.dto.ClientDemographicsUpdateRequest;
import zw.gov.mohcc.impilo.vito.api.dto.ExternalClientRegistrationRequest;
import zw.gov.mohcc.impilo.vito.api.dto.ExternalClientRegistrationResponse;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.service.ClientUpdateService;
import zw.gov.mohcc.impilo.vito.service.ExternalRegistrationService;

import java.util.UUID;

@RestController
@RequestMapping("/v1/clients")
public class ExternalClientController {

    private final ExternalRegistrationService externalRegistrationService;
    private final ClientUpdateService clientUpdateService;

    public ExternalClientController(ExternalRegistrationService externalRegistrationService,
                                    ClientUpdateService clientUpdateService) {
        this.externalRegistrationService = externalRegistrationService;
        this.clientUpdateService = clientUpdateService;
    }

    @PostMapping("/external")
    public ResponseEntity<ApiResponse<ExternalClientRegistrationResponse>> registerExternal(
            @Valid @RequestBody ExternalClientRegistrationRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        ExternalClientRegistrationResponse result = externalRegistrationService.register(
                ctx.tenantId(),
                request,
                ctx.actorId(),
                ctx.actorType(),
                ctx.correlationId().toString());
        HttpStatus status = switch (result.syncStatus()) {
            case "CREATED" -> HttpStatus.CREATED;
            case "PENDING_REVIEW" -> HttpStatus.ACCEPTED;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.ok(result, ctx.correlationId().toString()));
    }

    @PutMapping("/{healthId}")
    public ResponseEntity<ApiResponse<ClientEntity>> updateDemographics(
            @PathVariable UUID healthId,
            @RequestBody ClientDemographicsUpdateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (!"SYSTEM".equalsIgnoreCase(ctx.actorType())) {
            throw new AccessDeniedException("Client demographics update requires SYSTEM actor type");
        }
        ClientEntity updated = clientUpdateService.update(
                ctx.tenantId(),
                healthId,
                request,
                ctx.actorId(),
                ctx.correlationId().toString());
        return ResponseEntity.ok(ApiResponse.ok(updated, ctx.correlationId().toString()));
    }
}
