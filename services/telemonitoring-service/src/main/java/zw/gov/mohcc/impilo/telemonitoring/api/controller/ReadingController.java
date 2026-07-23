package zw.gov.mohcc.impilo.telemonitoring.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.TelemetryReadingEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.TelemetryReadingRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Internal read surface over the clinical reading lane (OF-B25 scope item 6) — feeds the
 * later OF-B27/OF-B28 monitoring surfaces via the BFF. Newest first, paged. Quarantined
 * rows appear with their coded reason and NULL patient attribution (journey #62: they
 * exist as evidence, never as chart content — the SHR write columns prove what did and
 * did not reach the record).
 */
@RestController
@RequestMapping("/internal/v1/readings")
public class ReadingController {

    private static final int MAX_PAGE_SIZE = 200;

    private final TelemetryReadingRepository readingRepository;

    public ReadingController(TelemetryReadingRepository readingRepository) {
        this.readingRepository = readingRepository;
    }

    @GetMapping("/by-plan/{planId}")
    public PagedReadings byPlan(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<TelemetryReadingEntity> result = readingRepository
                .findByTenantIdAndPlanIdOrderByMeasuredAtDesc(tenantId, planId, PageRequest.of(page, clamp(size)));
        return PagedReadings.from(result, page);
    }

    @GetMapping("/by-device/{deviceRef}")
    public PagedReadings byDevice(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable String deviceRef,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<TelemetryReadingEntity> result = readingRepository
                .findByTenantIdAndDeviceRefOrderByMeasuredAtDesc(tenantId, deviceRef, PageRequest.of(page, clamp(size)));
        return PagedReadings.from(result, page);
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    public record ReadingResponse(
            UUID id,
            String deviceRef,
            UUID planId,
            String patientCpid,
            String metricCode,
            BigDecimal metricValue,
            String unit,
            OffsetDateTime measuredAt,
            OffsetDateTime receivedAt,
            String status,
            String quarantineReason,
            String qualityStamp,
            String shrWriteState,
            OffsetDateTime shrWrittenAt,
            String shrRef) {

        static ReadingResponse from(TelemetryReadingEntity e) {
            return new ReadingResponse(e.getId(), e.getDeviceRef(), e.getPlanId(), e.getPatientCpid(),
                    e.getMetricCode(), e.getMetricValue(), e.getUnit(), e.getMeasuredAt(), e.getReceivedAt(),
                    e.getStatus().name(), e.getQuarantineReason(), e.getQualityStamp(),
                    e.getShrWriteState().name(), e.getShrWrittenAt(), e.getShrRef());
        }
    }

    public record PagedReadings(List<ReadingResponse> readings, int page, long totalElements, int totalPages) {

        static PagedReadings from(Page<TelemetryReadingEntity> result, int page) {
            return new PagedReadings(
                    result.getContent().stream().map(ReadingResponse::from).toList(),
                    page, result.getTotalElements(), result.getTotalPages());
        }
    }
}
