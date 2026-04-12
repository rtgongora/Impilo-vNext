package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.AddNetworkMemberRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.CreateProviderContractRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.CreateProviderNetworkRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.ProviderNetworkSummaryResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.SuspendContractRequest;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.NetworkMemberEntity;
import zw.gov.mohcc.impilo.coverage.domain.ProviderContractEntity;
import zw.gov.mohcc.impilo.coverage.domain.ProviderNetworkEntity;
import zw.gov.mohcc.impilo.coverage.repository.NetworkMemberRepository;
import zw.gov.mohcc.impilo.coverage.repository.ProviderContractRepository;
import zw.gov.mohcc.impilo.coverage.repository.ProviderNetworkRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal APIs for provider contracting and preferred provider networks (v1.3 foundation).
 */
@RestController
@RequestMapping("/internal/v1")
public class ProviderContractController {

    private final ProviderContractRepository contractRepository;
    private final ProviderNetworkRepository networkRepository;
    private final NetworkMemberRepository memberRepository;
    private final CoverageEventService eventService;

    public ProviderContractController(ProviderContractRepository contractRepository,
                                        ProviderNetworkRepository networkRepository,
                                        NetworkMemberRepository memberRepository,
                                        CoverageEventService eventService) {
        this.contractRepository = contractRepository;
        this.networkRepository = networkRepository;
        this.memberRepository = memberRepository;
        this.eventService = eventService;
    }

    // ── Provider contracts ───────────────────────────────────────────────

    @PostMapping("/provider-contracts")
    @Transactional
    public ResponseEntity<ProviderContractEntity> createContract(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @Valid @RequestBody CreateProviderContractRequest request) {
        UUID tid = UUID.fromString(tenantId);
        ProviderContractEntity e = new ProviderContractEntity();
        e.setTenantId(tid);
        e.setProviderId(request.providerId());
        e.setPayerId(request.payerId());
        e.setPlanCode(request.planCode());
        e.setContractType(Optional.ofNullable(request.contractType()).orElse("FEE_FOR_SERVICE"));
        e.setReimbursementMode(Optional.ofNullable(request.reimbursementMode()).orElse("FEE_FOR_SERVICE"));
        e.setEffectiveFrom(request.effectiveFrom());
        e.setEffectiveTo(request.effectiveTo());
        e.setRateScheduleJson(Optional.ofNullable(request.rateScheduleJson()).filter(s -> !s.isBlank()).orElse("{}"));
        e.setTermsJson(Optional.ofNullable(request.termsJson()).filter(s -> !s.isBlank()).orElse("{}"));
        e.setCreatedBy(request.createdBy());
        contractRepository.save(e);

        eventService.emitCreated("contract", e.getContractId().toString(),
                CorrelationIds.fromHeader(correlationId), tid, podId, contractState(e));
        return ResponseEntity.status(HttpStatus.CREATED).body(e);
    }

    @GetMapping("/provider-contracts")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProviderContractEntity>> listContracts(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String provider_id,
            @RequestParam(required = false) String payer_id,
            @RequestParam(required = false) String status) {
        UUID tid = UUID.fromString(tenantId);
        String p = blankToNull(provider_id);
        String pay = blankToNull(payer_id);
        String st = blankToNull(status);
        return ResponseEntity.ok(contractRepository.search(tid, p, pay, st));
    }

    @GetMapping("/provider-contracts/{contractId}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProviderContractEntity> getContract(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID contractId) {
        UUID tid = UUID.fromString(tenantId);
        return contractRepository.findByContractId(contractId)
                .filter(c -> c.getTenantId().equals(tid))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/provider-contracts/{contractId}/suspend")
    @Transactional
    public ResponseEntity<ProviderContractEntity> suspendContract(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @PathVariable UUID contractId,
            @Valid @RequestBody SuspendContractRequest request) {
        UUID tid = UUID.fromString(tenantId);
        ProviderContractEntity e = contractRepository.findByContractId(contractId)
                .filter(c -> c.getTenantId().equals(tid))
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));
        e.setStatus("SUSPENDED");
        e.setSuspendedAt(OffsetDateTime.now());
        e.setSuspensionReason(request.reason());
        contractRepository.save(e);

        eventService.emitStatusChange("contract", e.getContractId().toString(), "suspended",
                CorrelationIds.fromHeader(correlationId), tid, podId,
                Map.of("status", "SUSPENDED", "suspension_reason", request.reason()));
        return ResponseEntity.ok(e);
    }

    @PostMapping("/provider-contracts/{contractId}/reinstate")
    @Transactional
    public ResponseEntity<ProviderContractEntity> reinstateContract(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @PathVariable UUID contractId) {
        UUID tid = UUID.fromString(tenantId);
        ProviderContractEntity e = contractRepository.findByContractId(contractId)
                .filter(c -> c.getTenantId().equals(tid))
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));
        e.setStatus("ACTIVE");
        e.setSuspendedAt(null);
        e.setSuspensionReason(null);
        contractRepository.save(e);

        eventService.emitStatusChange("contract", e.getContractId().toString(), "reinstated",
                CorrelationIds.fromHeader(correlationId), tid, podId,
                Map.of("status", "ACTIVE"));
        return ResponseEntity.ok(e);
    }

    // ── Provider networks ────────────────────────────────────────────────

    @PostMapping("/provider-networks")
    @Transactional
    public ResponseEntity<ProviderNetworkEntity> createNetwork(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @Valid @RequestBody CreateProviderNetworkRequest request) {
        UUID tid = UUID.fromString(tenantId);
        ProviderNetworkEntity n = new ProviderNetworkEntity();
        n.setTenantId(tid);
        n.setName(request.name());
        n.setPayerId(request.payerId());
        n.setNetworkType(Optional.ofNullable(request.networkType()).orElse("PREFERRED"));
        n.setServiceArea(Optional.ofNullable(request.serviceArea()).filter(s -> !s.isBlank()).orElse("[]"));
        networkRepository.save(n);

        eventService.emitCreated("network", n.getNetworkId().toString(),
                CorrelationIds.fromHeader(correlationId), tid, podId, networkState(n));
        return ResponseEntity.status(HttpStatus.CREATED).body(n);
    }

    @GetMapping("/provider-networks")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProviderNetworkSummaryResponse>> listNetworks(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String payer_id,
            @RequestParam(required = false) String status) {
        UUID tid = UUID.fromString(tenantId);
        List<ProviderNetworkEntity> rows = networkRepository.search(tid, blankToNull(payer_id), blankToNull(status));
        List<ProviderNetworkSummaryResponse> out = rows.stream()
                .map(n -> ProviderNetworkSummaryResponse.of(
                        n, memberRepository.countByNetworkIdAndRemovedAtIsNull(n.getNetworkId())))
                .toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/provider-networks/{networkId}/members")
    @Transactional(readOnly = true)
    public ResponseEntity<List<NetworkMemberEntity>> listNetworkMembers(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID networkId) {
        UUID tid = UUID.fromString(tenantId);
        networkRepository.findByNetworkId(networkId)
                .filter(n -> n.getTenantId().equals(tid))
                .orElseThrow(() -> new IllegalArgumentException("Network not found"));
        return ResponseEntity.ok(memberRepository.findByNetworkIdAndRemovedAtIsNullOrderByJoinedAtDesc(networkId));
    }

    @PostMapping("/provider-networks/{networkId}/members")
    @Transactional
    public ResponseEntity<NetworkMemberEntity> addMember(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @PathVariable UUID networkId,
            @Valid @RequestBody AddNetworkMemberRequest request) {
        UUID tid = UUID.fromString(tenantId);
        ProviderNetworkEntity net = networkRepository.findByNetworkId(networkId)
                .filter(n -> n.getTenantId().equals(tid))
                .orElseThrow(() -> new IllegalArgumentException("Network not found"));

        memberRepository.findByNetworkIdAndProviderIdAndRemovedAtIsNull(networkId, request.providerId())
                .ifPresent(m -> {
                    throw new IllegalStateException("Provider already active in network");
                });

        NetworkMemberEntity m = new NetworkMemberEntity();
        m.setTenantId(tid);
        m.setNetworkId(net.getNetworkId());
        m.setProviderId(request.providerId());
        m.setContractId(request.contractId());
        memberRepository.save(m);

        eventService.emitCreated("network_member", m.getId().toString(),
                CorrelationIds.fromHeader(correlationId), tid, podId, memberState(m));
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @DeleteMapping("/provider-networks/{networkId}/members/{providerId}")
    @Transactional
    public ResponseEntity<Void> removeMember(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @PathVariable UUID networkId,
            @PathVariable String providerId) {
        UUID tid = UUID.fromString(tenantId);
        NetworkMemberEntity m = memberRepository.findByNetworkIdAndProviderIdAndRemovedAtIsNull(networkId, providerId)
                .filter(x -> x.getTenantId().equals(tid))
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        m.setRemovedAt(OffsetDateTime.now());
        memberRepository.save(m);

        eventService.emitStatusChange("network_member", m.getId().toString(), "removed",
                CorrelationIds.fromHeader(correlationId), tid, podId,
                Map.of("network_id", networkId.toString(), "provider_id", providerId));
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> contractState(ProviderContractEntity e) {
        Map<String, Object> m = new HashMap<>();
        m.put("contract_id", e.getContractId().toString());
        m.put("provider_id", e.getProviderId());
        m.put("payer_id", e.getPayerId());
        m.put("status", e.getStatus());
        return m;
    }

    private static Map<String, Object> networkState(ProviderNetworkEntity n) {
        Map<String, Object> m = new HashMap<>();
        m.put("network_id", n.getNetworkId().toString());
        m.put("name", n.getName());
        m.put("payer_id", n.getPayerId());
        m.put("status", n.getStatus());
        return m;
    }

    private static Map<String, Object> memberState(NetworkMemberEntity m) {
        Map<String, Object> map = new HashMap<>();
        map.put("member_id", m.getId());
        map.put("network_id", m.getNetworkId().toString());
        map.put("provider_id", m.getProviderId());
        map.put("contract_id", m.getContractId() != null ? m.getContractId().toString() : null);
        return map;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
