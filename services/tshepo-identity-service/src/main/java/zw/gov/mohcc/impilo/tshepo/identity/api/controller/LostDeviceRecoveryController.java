package zw.gov.mohcc.impilo.tshepo.identity.api.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.LostDeviceRecoveryDtos;
import zw.gov.mohcc.impilo.tshepo.identity.core.LostDeviceRecoveryService;

@RestController
@RequestMapping("/internal/v1/identity/recovery-cases")
public class LostDeviceRecoveryController {
    private final LostDeviceRecoveryService service;
    public LostDeviceRecoveryController(LostDeviceRecoveryService service){this.service=service;}
    @PostMapping public ResponseEntity<ApiResponse<LostDeviceRecoveryDtos.View>> create(
            @RequestHeader("X-Tenant-ID") UUID tenantId, @RequestHeader("X-Actor-ID") String actor,
            @Valid @RequestBody LostDeviceRecoveryDtos.CreateRequest body){return ResponseEntity.ok(ApiResponse.ok(service.create(tenantId,actor,body),null));}
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<LostDeviceRecoveryDtos.View>> get(
            @RequestHeader("X-Tenant-ID") UUID tenantId,@PathVariable UUID id){return ResponseEntity.ok(ApiResponse.ok(service.get(tenantId,id),null));}
    @PostMapping("/{id}/approval") public ResponseEntity<ApiResponse<LostDeviceRecoveryDtos.View>> approve(
            @RequestHeader("X-Tenant-ID") UUID tenantId,@RequestHeader("X-Actor-ID") String actor,
            @PathVariable UUID id,@RequestBody LostDeviceRecoveryDtos.ApprovalRequest body){return ResponseEntity.ok(ApiResponse.ok(service.approve(tenantId,id,actor,body),null));}
    @PostMapping("/{id}/execution") public ResponseEntity<ApiResponse<LostDeviceRecoveryDtos.View>> execution(
            @RequestHeader("X-Tenant-ID") UUID tenantId,@RequestHeader("X-Service-ID") String serviceId,
            @PathVariable UUID id,@RequestBody LostDeviceRecoveryDtos.ExecutionRequest body){if(!"experience-bff".equals(serviceId))throw new IllegalArgumentException("Recovery execution is restricted to the governed executor");return ResponseEntity.ok(ApiResponse.ok(service.recordExecution(tenantId,id,body),null));}
}
