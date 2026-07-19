package zw.gov.mohcc.impilo.ndila.core.place;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.domain.NdilaPlaceAnchorEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;
import zw.gov.mohcc.impilo.ndila.repository.NdilaPlaceAnchorRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Shared place anchors (D-L1). Stewarded operations only: an anchor is created
 * for a physical location/campus, and owner-keyed locations from ANY registry
 * (TUSO facility, INDAWO site, …) are assigned to it. The anchor is geo-only —
 * cross-registry *relationships* live in Indawo's {@code ind_place_links};
 * "same campus" geometry lives here.
 */
@Service
public class NdilaPlaceAnchorService {

    private static final Logger log = LoggerFactory.getLogger(NdilaPlaceAnchorService.class);

    private final NdilaPlaceAnchorRepository anchorRepository;
    private final NdilaLocationRepository locationRepository;

    public NdilaPlaceAnchorService(NdilaPlaceAnchorRepository anchorRepository,
                                   NdilaLocationRepository locationRepository) {
        this.anchorRepository = anchorRepository;
        this.locationRepository = locationRepository;
    }

    public record CreateAnchor(BigDecimal centroidLatitude, BigDecimal centroidLongitude,
                               String boundaryGeojson, String ward, String district,
                               String province, String campusLabel) {}

    @Transactional
    public NdilaPlaceAnchorEntity create(UUID tenantId, String createdBy, CreateAnchor request) {
        if (request.centroidLatitude() == null || request.centroidLongitude() == null) {
            throw new IllegalArgumentException("An anchor requires a centroid");
        }
        NdilaPlaceAnchorEntity anchor = new NdilaPlaceAnchorEntity();
        anchor.setTenantId(tenantId);
        anchor.setCentroidLatitude(request.centroidLatitude());
        anchor.setCentroidLongitude(request.centroidLongitude());
        anchor.setBoundaryGeojson(request.boundaryGeojson());
        anchor.setWard(request.ward());
        anchor.setDistrict(request.district());
        anchor.setProvince(request.province());
        anchor.setCampusLabel(request.campusLabel());
        anchor.setCreatedBy(createdBy);
        anchor = anchorRepository.save(anchor);
        log.info("place anchor created: id={} by {}", anchor.getId(), createdBy);
        return anchor;
    }

    /** Assign a location (any owner registry) to an anchor. Stewarded. */
    @Transactional
    public NdilaLocationEntity assign(UUID tenantId, UUID anchorId, UUID locationId) {
        NdilaPlaceAnchorEntity anchor = anchorRepository.findByIdAndTenantId(anchorId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown place anchor"));
        NdilaLocationEntity location = locationRepository.findById(locationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown location"));
        location.setAnchorId(anchor.getId());
        location = locationRepository.save(location);
        log.info("location {} ({}:{}) assigned to anchor {}",
                location.getId(), location.getOwnerService(), location.getOwnerEntityId(), anchor.getId());
        return location;
    }

    @Transactional
    public NdilaLocationEntity detach(UUID tenantId, UUID locationId) {
        NdilaLocationEntity location = locationRepository.findById(locationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown location"));
        location.setAnchorId(null);
        return locationRepository.save(location);
    }

    /** Every owner-keyed location sharing this anchor (the campus view). */
    @Transactional(readOnly = true)
    public List<NdilaLocationEntity> locations(UUID tenantId, UUID anchorId) {
        anchorRepository.findByIdAndTenantId(anchorId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown place anchor"));
        return locationRepository.findByTenantIdAndAnchorId(tenantId, anchorId);
    }
}
