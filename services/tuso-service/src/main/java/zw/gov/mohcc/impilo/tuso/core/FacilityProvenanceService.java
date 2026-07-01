package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityImportProvenanceDto;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierSystem;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityReadinessRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PractitionerInChargeAssignmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the import-provenance + configuration-completeness view for a facility. Everything is derived
 * from real state — TUSO-owned config is counted from its own repositories; downstream-owned config
 * (queues=PCT, workforce=Vashandi, stores=Dura) is honestly marked PENDING_DOWNSTREAM, never faked.
 */
@Service
public class FacilityProvenanceService {

    /** UI label for the public administrative code — deliberately never "Facility ID". */
    public static final String FACILITY_CODE_LABEL = "Facility code / administrative code";

    private final FacilityRepository facilityRepository;
    private final FacilityIdentifierRepository identifierRepository;
    private final ServicePointRepository servicePointRepository;
    private final WorkspaceRepository workspaceRepository;
    private final FacilityUnitRepository unitRepository;
    private final PractitionerInChargeAssignmentRepository picRepository;
    private final FacilityReadinessRepository readinessRepository;

    public FacilityProvenanceService(
            FacilityRepository facilityRepository,
            FacilityIdentifierRepository identifierRepository,
            ServicePointRepository servicePointRepository,
            WorkspaceRepository workspaceRepository,
            FacilityUnitRepository unitRepository,
            PractitionerInChargeAssignmentRepository picRepository,
            FacilityReadinessRepository readinessRepository) {
        this.facilityRepository = facilityRepository;
        this.identifierRepository = identifierRepository;
        this.servicePointRepository = servicePointRepository;
        this.workspaceRepository = workspaceRepository;
        this.unitRepository = unitRepository;
        this.picRepository = picRepository;
        this.readinessRepository = readinessRepository;
    }

    @Transactional(readOnly = true)
    public Optional<FacilityImportProvenanceDto> buildProvenance(Long facilityId) {
        return facilityRepository.findById(facilityId).map(this::build);
    }

    FacilityImportProvenanceDto build(FacilityEntity f) {
        Map<String, Object> meta = f.getMetadata() != null ? f.getMetadata() : Map.of();

        List<FacilityImportProvenanceDto.ExternalIdentifier> externals = new ArrayList<>();
        for (FacilityIdentifierEntity id : identifierRepository.findByFacilityIdAndActiveTrue(f.getId())) {
            externals.add(new FacilityImportProvenanceDto.ExternalIdentifier(
                    id.getSystem(), id.getValue(), FacilityIdentifierSystem.issuingAuthority(id.getSystem())));
        }

        String sourceLabel = str(meta.get("source_label"));
        boolean importedFromMaster = sourceLabel != null || meta.containsKey("import_pack_id");
        String sourceRow = deriveSourceRow(str(meta.get("facility_uid")));

        var identity = new FacilityImportProvenanceDto.Identity(
                f.getId(),
                f.getFacilityUuid() != null ? f.getFacilityUuid().toString() : null,
                f.getFacilityCode(),
                FACILITY_CODE_LABEL,
                externals,
                sourceLabel,
                sourceRow,
                importedFromMaster,
                f.getRegulatoryStatus() != null ? f.getRegulatoryStatus().name() : null,
                f.getOperationalStatus());

        var acceptableMissing = new FacilityImportProvenanceDto.AcceptableMissing(
                f.getLatitude() == null,
                f.getLongitude() == null,
                isBlank(f.getFacilityType()),
                isBlank(f.getOwnership()),
                isBlank(f.getOperationalStatus()) || "MISSING_REQUIRES_CONFIRMATION".equals(f.getOperationalStatus()));

        List<FacilityImportProvenanceDto.ChecklistItem> checklist = new ArrayList<>();
        boolean picPresent = !picRepository.findByFacilityIdOrderByStartDateDesc(f.getId()).isEmpty();
        checklist.add(item("practitioner_in_charge", "Practitioner in charge", present(picPresent), "TUSO"));
        boolean regulated = f.getRegulatoryStatus() == FacilityRegulatoryStatus.REGISTERED_ACTIVE;
        checklist.add(item("regulatory_compliance", "Regulatory compliance", present(regulated), "TUSO"));
        boolean servicePoints = servicePointRepository.countByFacilityIdAndActiveTrue(f.getId()) > 0;
        checklist.add(item("service_points", "Service points", present(servicePoints), "TUSO"));
        boolean workspaces = !workspaceRepository.findByFacilityIdAndActiveTrue(f.getId()).isEmpty();
        checklist.add(item("workspaces", "Workspaces", present(workspaces), "TUSO"));
        boolean units = !unitRepository.findByFacilityIdOrderByCreatedAtAsc(f.getId()).isEmpty();
        checklist.add(item("units", "Facility units", present(units), "TUSO"));
        boolean readiness = readinessRepository.findByFacilityId(f.getId()).isPresent();
        checklist.add(item("digital_readiness", "Digital readiness", present(readiness), "TUSO"));
        // Downstream-owned configuration — materialised by other services, not TUSO. Honestly pending.
        checklist.add(item("queues", "Queues", "PENDING_DOWNSTREAM", "PCT"));
        checklist.add(item("workforce", "Workforce setup", "PENDING_DOWNSTREAM", "VASHANDI"));
        checklist.add(item("stores", "Stock / store setup", "PENDING_DOWNSTREAM", "DURA"));

        String downstream = f.getRegulatoryStatus() == FacilityRegulatoryStatus.IMPORTED_PENDING_CONFIGURATION
                ? "PENDING_CONFIGURATION"
                : (checklist.stream().allMatch(c -> "PRESENT".equals(c.status())) ? "COMPLETE" : "INCOMPLETE");

        return new FacilityImportProvenanceDto(identity, acceptableMissing, checklist, downstream);
    }

    private static FacilityImportProvenanceDto.ChecklistItem item(String key, String label, String status, String owner) {
        return new FacilityImportProvenanceDto.ChecklistItem(key, label, status, owner);
    }

    private static String present(boolean b) {
        return b ? "PRESENT" : "MISSING";
    }

    /** Extract the source row number from a "MHF-<label>-row<N>" correlation key, when present. */
    private static String deriveSourceRow(String facilityUid) {
        if (facilityUid == null) {
            return null;
        }
        int i = facilityUid.lastIndexOf("row");
        if (i >= 0 && i + 3 < facilityUid.length()) {
            return facilityUid.substring(i + 3);
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
