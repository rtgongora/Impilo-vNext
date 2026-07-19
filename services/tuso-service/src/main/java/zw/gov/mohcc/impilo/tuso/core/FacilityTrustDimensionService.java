package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCapabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAdminAppointmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityTrustDimensionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityAdminAppointmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCapabilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityTrustDimensionRepository;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Materialises the 9-dimension facility trust model (Place Journey Doctrine §4,
 * D-L8) from the underlying signals. Honest by construction: a dimension whose
 * signal is not yet wired (EXISTENCE/COMPLIANCE before the W6 verification and
 * inspection feeds) is UNKNOWN — never inferred. The per-source legitimacy
 * composite stays the platform-access GATE; here it feeds LEGAL_IDENTITY only.
 */
@Service
public class FacilityTrustDimensionService {

    private static final Logger log = LoggerFactory.getLogger(FacilityTrustDimensionService.class);

    private static final EnumSet<FacilityRegulatoryStatus> REGULATORY_CONFIRMED = EnumSet.of(
            FacilityRegulatoryStatus.APPROVED_FOR_REGISTRATION,
            FacilityRegulatoryStatus.REGISTERED_ACTIVE,
            FacilityRegulatoryStatus.RENEWAL_DUE,
            FacilityRegulatoryStatus.RENEWAL_IN_PROGRESS);

    private static final EnumSet<FacilityRegulatoryStatus> REGULATORY_FAILED = EnumSet.of(
            FacilityRegulatoryStatus.RESTRICTED,
            FacilityRegulatoryStatus.SUSPENDED,
            FacilityRegulatoryStatus.PENDING_CLOSURE,
            FacilityRegulatoryStatus.CLOSED,
            FacilityRegulatoryStatus.VOLUNTARILY_CLOSED);

    private final FacilityRepository facilityRepository;
    private final FacilitySourceLegitimacyRepository legitimacyRepository;
    private final FacilityAdminAppointmentRepository appointmentRepository;
    private final FacilityCapabilityRepository capabilityRepository;
    private final FacilityTrustDimensionRepository dimensionRepository;

    public FacilityTrustDimensionService(FacilityRepository facilityRepository,
                                         FacilitySourceLegitimacyRepository legitimacyRepository,
                                         FacilityAdminAppointmentRepository appointmentRepository,
                                         FacilityCapabilityRepository capabilityRepository,
                                         FacilityTrustDimensionRepository dimensionRepository) {
        this.facilityRepository = facilityRepository;
        this.legitimacyRepository = legitimacyRepository;
        this.appointmentRepository = appointmentRepository;
        this.capabilityRepository = capabilityRepository;
        this.dimensionRepository = dimensionRepository;
    }

    /** Recompute and upsert all 9 dimensions for a facility. Returns the fresh rows. */
    @Transactional
    public List<FacilityTrustDimensionEntity> recompute(UUID tenantId, UUID facilityUuid) {
        FacilityEntity facility = facilityRepository.findByFacilityUuid(facilityUuid)
                .filter(f -> tenantId.equals(f.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility: " + facilityUuid));

        Map<String, Verdict> verdicts = new LinkedHashMap<>();
        // Not yet wired signals stay honestly UNKNOWN (W6 verification cases, inspection feed).
        verdicts.put("EXISTENCE", new Verdict(FacilityTrustDimensionEntity.STATUS_UNKNOWN,
                null, "verification-cases"));
        verdicts.put("GEOSPATIAL", geospatial(facility));
        verdicts.put("AUTHORITY", authority(facility));
        verdicts.put("LEGAL_IDENTITY", legalIdentity(facility));
        verdicts.put("REGULATORY", regulatory(facility));
        verdicts.put("CAPABILITY", capability(facility));
        verdicts.put("OPERATIONAL", operational(facility));
        verdicts.put("COMPLIANCE", new Verdict(FacilityTrustDimensionEntity.STATUS_UNKNOWN,
                null, "inspection-feed"));
        verdicts.put("PUBLIC_DISCLOSURE", publicDisclosure(facility));

        Instant now = Instant.now();
        List<FacilityTrustDimensionEntity> rows = verdicts.entrySet().stream().map(e -> {
            FacilityTrustDimensionEntity row = dimensionRepository
                    .findByTenantIdAndFacilityUuidAndDimension(tenantId, facilityUuid, e.getKey())
                    .orElseGet(FacilityTrustDimensionEntity::new);
            row.setTenantId(tenantId);
            row.setFacilityUuid(facilityUuid);
            row.setDimension(e.getKey());
            row.setStatus(e.getValue().status());
            row.setEvidenceRef(e.getValue().evidenceRef());
            row.setSource(e.getValue().source());
            row.setComputedAt(now);
            return dimensionRepository.save(row);
        }).toList();
        log.debug("trust dimensions recomputed for facility {}", facilityUuid);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<FacilityTrustDimensionEntity> view(UUID tenantId, UUID facilityUuid) {
        return dimensionRepository.findByTenantIdAndFacilityUuidOrderByDimensionAsc(tenantId, facilityUuid);
    }

    private Verdict geospatial(FacilityEntity facility) {
        boolean hasCoordinates = facility.getLatitude() != null && facility.getLongitude() != null;
        // Coordinates present = PARTIAL only; CONFIRMED needs anchor/GPS verification (W6/Ndila).
        return hasCoordinates
                ? new Verdict(FacilityTrustDimensionEntity.STATUS_PARTIAL, "facility:coordinates", "tuso")
                : new Verdict(FacilityTrustDimensionEntity.STATUS_UNKNOWN, null, "tuso");
    }

    private Verdict authority(FacilityEntity facility) {
        boolean anyActiveAdmin = appointmentRepository.existsByFacilityUuidAndApprovalState(
                facility.getFacilityUuid(), FacilityAdminAppointmentEntity.STATE_ACTIVE);
        return anyActiveAdmin
                ? new Verdict(FacilityTrustDimensionEntity.STATUS_CONFIRMED,
                        "facility-admin-appointment:ACTIVE", "tuso")
                : new Verdict(FacilityTrustDimensionEntity.STATUS_UNKNOWN, null, "tuso");
    }

    private Verdict legalIdentity(FacilityEntity facility) {
        List<FacilitySourceLegitimacyEntity> rows =
                legitimacyRepository.findByFacilityIdOrderBySourceAsc(facility.getFacilityUuid());
        if (rows.isEmpty()) {
            return new Verdict(FacilityTrustDimensionEntity.STATUS_UNKNOWN, null, "source-legitimacy");
        }
        boolean anyDenies = rows.stream().anyMatch(r -> !r.isAllowedOnPlatform());
        boolean anyAllows = rows.stream().anyMatch(FacilitySourceLegitimacyEntity::isAllowedOnPlatform);
        if (!anyDenies && anyAllows) {
            return new Verdict(FacilityTrustDimensionEntity.STATUS_CONFIRMED,
                    "source-legitimacy:composite-allow", "source-legitimacy");
        }
        return new Verdict(FacilityTrustDimensionEntity.STATUS_FAILED,
                "source-legitimacy:composite-deny", "source-legitimacy");
    }

    private Verdict regulatory(FacilityEntity facility) {
        FacilityRegulatoryStatus status = facility.getRegulatoryStatus();
        if (status == null) {
            return new Verdict(FacilityTrustDimensionEntity.STATUS_UNKNOWN, null, "regulatory");
        }
        if (REGULATORY_CONFIRMED.contains(status)) {
            return new Verdict(FacilityTrustDimensionEntity.STATUS_CONFIRMED,
                    "regulatory-status:" + status, "regulatory");
        }
        if (REGULATORY_FAILED.contains(status)) {
            return new Verdict(FacilityTrustDimensionEntity.STATUS_FAILED,
                    "regulatory-status:" + status, "regulatory");
        }
        return new Verdict(FacilityTrustDimensionEntity.STATUS_PARTIAL,
                "regulatory-status:" + status, "regulatory");
    }

    private Verdict capability(FacilityEntity facility) {
        List<FacilityCapabilityEntity> active =
                capabilityRepository.findByFacilityIdAndActiveTrue(facility.getId());
        if (active.isEmpty()) {
            return new Verdict(FacilityTrustDimensionEntity.STATUS_UNKNOWN, null, "capability");
        }
        boolean anyValidated = active.stream()
                .anyMatch(c -> Boolean.TRUE.equals(c.getZiboValidated()));
        return anyValidated
                ? new Verdict(FacilityTrustDimensionEntity.STATUS_CONFIRMED,
                        "capabilities:zibo-validated", "capability")
                : new Verdict(FacilityTrustDimensionEntity.STATUS_PARTIAL,
                        "capabilities:declared", "capability");
    }

    private Verdict operational(FacilityEntity facility) {
        String status = facility.getOperationalStatus();
        if (status == null || status.isBlank()) {
            return new Verdict(FacilityTrustDimensionEntity.STATUS_UNKNOWN, null, "operational");
        }
        if ("OPERATIONAL".equalsIgnoreCase(status)) {
            return new Verdict(FacilityTrustDimensionEntity.STATUS_CONFIRMED,
                    "operational-status:OPERATIONAL", "operational");
        }
        return new Verdict(FacilityTrustDimensionEntity.STATUS_FAILED,
                "operational-status:" + status, "operational");
    }

    private Verdict publicDisclosure(FacilityEntity facility) {
        boolean active = "ACTIVE".equalsIgnoreCase(facility.getStatus());
        return active
                ? new Verdict(FacilityTrustDimensionEntity.STATUS_CONFIRMED,
                        "facility-status:ACTIVE", "projection")
                : new Verdict(FacilityTrustDimensionEntity.STATUS_PARTIAL,
                        "facility-status:" + facility.getStatus(), "projection");
    }

    private record Verdict(String status, String evidenceRef, String source) {}
}
