package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.QualitySignalEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.QualitySignalRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Owns the {@code rit_quality_signal} table: the ingestion point for system / integration
 * quality signals (from PCT, TUSO, INDAWO, MADI, etc.). Signals are triaged by a reviewer
 * and, where warranted, converted into a governed Rito case via {@link CaseService}. The
 * signal itself is advisory data — only a case carries the operating lifecycle.
 */
@Service
public class QualitySignalService {

    private final QualitySignalRepository repository;
    private final RitoEventEmitter emitter;
    private final CaseService caseService;

    public QualitySignalService(QualitySignalRepository repository, RitoEventEmitter emitter,
                                CaseService caseService) {
        this.repository = repository;
        this.emitter = emitter;
        this.caseService = caseService;
    }

    @Transactional
    public QualitySignalEntity ingest(UUID tenantId, String signalType, String sourceSystem,
                                      String sourceRef, String severity, UUID facilityId,
                                      UUID districtId, String summary, Map<String, Object> detail) {
        QualitySignalEntity signal = new QualitySignalEntity();
        signal.setTenantId(tenantId);
        signal.setSignalType(signalType);
        signal.setSourceSystem(sourceSystem);
        signal.setSourceRef(sourceRef);
        signal.setSeverity(severity != null ? severity : "LOW");
        signal.setFacilityId(facilityId);
        signal.setDistrictId(districtId);
        signal.setSummary(summary);
        signal.setDetailJson(JsonUtil.toJson(detail));
        signal.setStatus("NEW");
        repository.save(signal);

        emitter.emit("QUALITY_SIGNAL", signal.getId().toString(), "rito.quality.signal.created",
                "SIGNAL", signal.getId().toString(),
                Map.of("signalId", signal.getId().toString(), "signalType", signalType,
                        "sourceSystem", sourceSystem, "severity", signal.getSeverity()),
                tenantId);
        return signal;
    }

    @Transactional(readOnly = true)
    public List<QualitySignalEntity> list(UUID tenantId, String status) {
        if (status != null) {
            return repository.findByTenantIdAndStatusOrderByReceivedAtDesc(tenantId, status);
        }
        return repository.findByTenantIdOrderByReceivedAtDesc(tenantId);
    }

    @Transactional
    public QualitySignalEntity review(UUID tenantId, UUID signalId, String reviewedBy, boolean dismiss) {
        QualitySignalEntity signal = repository.findByIdAndTenantId(signalId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Quality signal not found: " + signalId));
        signal.setStatus(dismiss ? "DISMISSED" : "REVIEWED");
        signal.setReviewedBy(reviewedBy);
        signal.setReviewedAt(OffsetDateTime.now());
        repository.save(signal);

        emitter.emit("QUALITY_SIGNAL", signal.getId().toString(), "rito.quality.signal.reviewed",
                "SIGNAL", signal.getId().toString(),
                Map.of("signalId", signal.getId().toString(), "status", signal.getStatus(),
                        "reviewedBy", reviewedBy != null ? reviewedBy : ""),
                tenantId);
        return signal;
    }

    @Transactional
    public CaseEntity convertToCase(UUID tenantId, UUID signalId, String caseType, String severity,
                                    String actorId, String actorType) {
        QualitySignalEntity signal = repository.findByIdAndTenantId(signalId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Quality signal not found: " + signalId));

        String title = signal.getSummary() != null ? signal.getSummary() : signal.getSignalType();
        CaseService.NewCase newCase = new CaseService.NewCase(
                caseType != null ? caseType : "QUALITY_FINDING",
                title,
                signal.getSummary(),
                severity != null ? severity : signal.getSeverity(),
                "SYSTEM_SIGNAL",
                null,
                false,
                signal.getFacilityId(),
                signal.getDistrictId(),
                null,
                null,
                actorId,
                actorType != null ? actorType : "SYSTEM",
                null,
                false,
                Map.of("sourceSignalId", signalId.toString(), "sourceSystem", signal.getSourceSystem()));

        CaseEntity created = caseService.createCase(tenantId, newCase);

        caseService.addLink(tenantId, created.getId(), linkTypeFor(signal.getSourceSystem()),
                signal.getSourceRef() != null ? signal.getSourceRef() : signalId.toString(),
                signal.getSourceSystem(), "Converted from quality signal");

        signal.setCaseId(created.getId());
        signal.setStatus("CONVERTED_TO_CASE");
        repository.save(signal);
        return created;
    }

    // ---- internal helpers ----

    private static String linkTypeFor(String sourceSystem) {
        if (sourceSystem == null) {
            return "FACILITY";
        }
        return switch (sourceSystem.toUpperCase()) {
            case "PCT" -> "ENCOUNTER";
            case "TUSO" -> "FACILITY";
            case "INDAWO" -> "PUBLIC_HEALTH_SITE";
            case "MADI" -> "CLIENT";
            default -> "FACILITY";
        };
    }
}
