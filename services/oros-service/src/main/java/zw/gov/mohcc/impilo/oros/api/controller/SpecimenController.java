package zw.gov.mohcc.impilo.oros.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.SpecimenDto;
import zw.gov.mohcc.impilo.oros.core.SpecimenService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Laboratory specimen lifecycle endpoints (spec §7): collection, labelling, dispatch, receipt,
 * rejection, and recollection. Authorization is enforced at the Envoy/TSHEPO edge; each action
 * drives the order's LAB workflow and emits an audit event via {@link SpecimenService}.
 */
@RestController
@RequestMapping("/v1")
public class SpecimenController {

    private final SpecimenService specimenService;

    public SpecimenController(SpecimenService specimenService) {
        this.specimenService = specimenService;
    }

    public record CollectSpecimenRequest(String specimenType, String specimenSource, String bodySite,
                                         String fastingStatus, String collectionSite,
                                         String collectionInstructions, String sampleId) {}

    public record LabelSpecimenRequest(String sampleId) {}

    public record ReceiveSpecimenRequest(String labNumber) {}

    public record ReasonRequest(String reason) {}

    /** Record a specimen collected against an order. */
    @PostMapping("/orders/{orderId}/specimens")
    public ResponseEntity<ApiResponse<SpecimenDto>> collect(
            @PathVariable String orderId,
            @RequestBody(required = false) CollectSpecimenRequest request) {
        String cid = correlationId();
        CollectSpecimenRequest r = request != null ? request
                : new CollectSpecimenRequest(null, null, null, null, null, null, null);
        SpecimenDto dto = SpecimenDto.from(specimenService.collect(
                orderId, r.specimenType(), r.specimenSource(), r.bodySite(), r.fastingStatus(),
                r.collectionSite(), r.collectionInstructions(), r.sampleId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto, cid));
    }

    /** List the specimens for an order. */
    @GetMapping("/orders/{orderId}/specimens")
    public ResponseEntity<ApiResponse<List<SpecimenDto>>> list(@PathVariable String orderId) {
        List<SpecimenDto> dtos = specimenService.listForOrder(orderId).stream()
                .map(SpecimenDto::from).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId()));
    }

    @PostMapping("/specimens/{specimenId}/label")
    public ResponseEntity<ApiResponse<SpecimenDto>> label(
            @PathVariable UUID specimenId,
            @RequestBody(required = false) LabelSpecimenRequest request) {
        SpecimenDto dto = SpecimenDto.from(specimenService.label(
                specimenId, request != null ? request.sampleId() : null));
        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId()));
    }

    @PostMapping("/specimens/{specimenId}/dispatch")
    public ResponseEntity<ApiResponse<SpecimenDto>> dispatch(@PathVariable UUID specimenId) {
        return ResponseEntity.ok(ApiResponse.ok(
                SpecimenDto.from(specimenService.dispatch(specimenId)), correlationId()));
    }

    @PostMapping("/specimens/{specimenId}/receive")
    public ResponseEntity<ApiResponse<SpecimenDto>> receive(
            @PathVariable UUID specimenId,
            @RequestBody(required = false) ReceiveSpecimenRequest request) {
        SpecimenDto dto = SpecimenDto.from(specimenService.receive(
                specimenId, request != null ? request.labNumber() : null));
        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId()));
    }

    @PostMapping("/specimens/{specimenId}/reject")
    public ResponseEntity<ApiResponse<SpecimenDto>> reject(
            @PathVariable UUID specimenId,
            @RequestBody(required = false) ReasonRequest request) {
        SpecimenDto dto = SpecimenDto.from(specimenService.reject(
                specimenId, request != null ? request.reason() : null));
        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId()));
    }

    @PostMapping("/specimens/{specimenId}/recollect")
    public ResponseEntity<ApiResponse<SpecimenDto>> recollect(
            @PathVariable UUID specimenId,
            @RequestBody(required = false) ReasonRequest request) {
        SpecimenDto dto = SpecimenDto.from(specimenService.requestRecollection(
                specimenId, request != null ? request.reason() : null));
        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId()));
    }

    private String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
