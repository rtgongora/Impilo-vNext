package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityPremisesEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityPremisesOccupancyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityPremisesOccupancyRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityPremisesRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Premises/site identity — separate from the regulated facility (HPA-2017-V1
 * pp.8-9). One address can host multiple separately regulated entities: the
 * shared-premises arrangement is simply more than one active occupancy on the
 * same premises. Relocation never transfers a certificate; it ends the old
 * occupancy historically and creates a new one through a governed application.
 */
@Service
public class PremisesService {

    private static final Logger log = LoggerFactory.getLogger(PremisesService.class);

    private final FacilityPremisesRepository premisesRepository;
    private final FacilityPremisesOccupancyRepository occupancyRepository;
    private final FacilityRepository facilityRepository;

    public PremisesService(FacilityPremisesRepository premisesRepository,
                           FacilityPremisesOccupancyRepository occupancyRepository,
                           FacilityRepository facilityRepository) {
        this.premisesRepository = premisesRepository;
        this.occupancyRepository = occupancyRepository;
        this.facilityRepository = facilityRepository;
    }

    public record CreatePremisesRequest(String label, String addressLine1, String addressLine2,
                                        String city, String province, String district, String ward,
                                        String postalCode, Double latitude, Double longitude,
                                        String occupancyTenure, String tenureEvidenceRef,
                                        String localAuthorityReportRef, Map<String, Object> metadata) {}

    @Transactional
    public FacilityPremisesEntity createPremises(CreatePremisesRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (request.label() == null || request.label().isBlank()) {
            throw new IllegalArgumentException("Premises label is required");
        }
        FacilityPremisesEntity premises = new FacilityPremisesEntity();
        premises.setTenantId(ctx.tenantId());
        premises.setLabel(request.label());
        premises.setAddressLine1(request.addressLine1());
        premises.setAddressLine2(request.addressLine2());
        premises.setCity(request.city());
        premises.setProvince(request.province());
        premises.setDistrict(request.district());
        premises.setWard(request.ward());
        premises.setPostalCode(request.postalCode());
        premises.setLatitude(request.latitude() != null ? java.math.BigDecimal.valueOf(request.latitude()) : null);
        premises.setLongitude(request.longitude() != null ? java.math.BigDecimal.valueOf(request.longitude()) : null);
        premises.setOccupancyTenure(request.occupancyTenure());
        premises.setTenureEvidenceRef(request.tenureEvidenceRef());
        premises.setLocalAuthorityReportRef(request.localAuthorityReportRef());
        premises.setStatus("ACTIVE");
        premises.setMetadata(request.metadata());
        premises.setCreatedBy(ctx.actorId());
        premises.setUpdatedBy(ctx.actorId());
        return premisesRepository.save(premises);
    }

    /** Occupy a premises. role PRIMARY = the facility's operating site; SHARED_OCCUPANT = co-location. */
    @Transactional
    public FacilityPremisesOccupancyEntity assignOccupancy(UUID premisesId, Long facilityId,
                                                           String role, UUID relocationApplicationId,
                                                           String notes) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityPremisesEntity premises = requirePremises(premisesId, ctx);
        facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        String occupancyRole = role != null && !role.isBlank() ? role.toUpperCase() : "PRIMARY";

        // A facility has at most one ACTIVE primary occupancy — end the previous one historically.
        if ("PRIMARY".equals(occupancyRole)) {
            for (FacilityPremisesOccupancyEntity current
                    : occupancyRepository.findByFacilityIdAndEffectiveToIsNull(facilityId)) {
                if ("PRIMARY".equals(current.getOccupancyRole())) {
                    current.setEffectiveTo(LocalDate.now());
                    current.setRelocationApplicationId(relocationApplicationId);
                    occupancyRepository.save(current);
                    log.info("Ended primary occupancy {} for facility {} (relocation)", current.getOccupancyId(), facilityId);
                }
            }
        }

        FacilityPremisesOccupancyEntity occupancy = new FacilityPremisesOccupancyEntity();
        occupancy.setPremisesId(premises.getPremisesId());
        occupancy.setFacilityId(facilityId);
        occupancy.setOccupancyRole(occupancyRole);
        occupancy.setEffectiveFrom(LocalDate.now());
        occupancy.setRelocationApplicationId(relocationApplicationId);
        occupancy.setNotes(notes);
        occupancy.setCreatedBy(ctx.actorId());
        return occupancyRepository.save(occupancy);
    }

    @Transactional(readOnly = true)
    public List<FacilityPremisesEntity> listPremises() {
        TrustContext ctx = TrustContextHolder.require();
        return premisesRepository.findByTenantIdAndStatus(ctx.tenantId(), "ACTIVE");
    }

    @Transactional(readOnly = true)
    public FacilityPremisesEntity getPremises(UUID premisesId) {
        return requirePremises(premisesId, TrustContextHolder.require());
    }

    /** All active occupants of a premises — >1 means a shared-premises arrangement. */
    @Transactional(readOnly = true)
    public List<FacilityPremisesOccupancyEntity> listOccupants(UUID premisesId) {
        return occupancyRepository.findByPremisesIdAndEffectiveToIsNull(premisesId);
    }

    /** A facility's occupancy history — current and past premises, never overwritten. */
    @Transactional(readOnly = true)
    public List<FacilityPremisesOccupancyEntity> occupancyHistory(Long facilityId) {
        return occupancyRepository.findByFacilityId(facilityId);
    }

    private FacilityPremisesEntity requirePremises(UUID premisesId, TrustContext ctx) {
        FacilityPremisesEntity premises = premisesRepository.findById(premisesId)
                .orElseThrow(() -> new IllegalArgumentException("Premises not found: " + premisesId));
        if (!premises.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }
        return premises;
    }
}
