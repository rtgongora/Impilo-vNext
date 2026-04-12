package zw.gov.mohcc.impilo.community.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.community.api.dto.CreateCommunityUnitRequest;
import zw.gov.mohcc.impilo.community.persistence.entity.CommunityUnitEntity;
import zw.gov.mohcc.impilo.community.persistence.repository.CommunityUnitRepository;

import java.util.List;
import java.util.UUID;

@Service
public class CommunityUnitService {

    private final CommunityUnitRepository communityUnitRepository;

    public CommunityUnitService(CommunityUnitRepository communityUnitRepository) {
        this.communityUnitRepository = communityUnitRepository;
    }

    @Transactional
    public CommunityUnitEntity createUnit(CreateCommunityUnitRequest request) {
        CommunityUnitEntity entity = new CommunityUnitEntity();
        entity.setTenantId(request.tenantId());
        entity.setName(request.name());
        entity.setUnitType(request.unitType());
        entity.setFacilityId(request.facilityId());
        entity.setProvince(request.province());
        entity.setDistrict(request.district());
        entity.setWard(request.ward());
        entity.setCatchmentPopulation(request.catchmentPopulation());
        entity.setStatus("ACTIVE");
        return communityUnitRepository.save(entity);
    }

    public List<CommunityUnitEntity> listUnits(UUID tenantId) {
        if (tenantId != null) {
            return communityUnitRepository.findByTenantIdOrderByNameAsc(tenantId);
        }
        return communityUnitRepository.findAll();
    }

    public CommunityUnitEntity getUnit(UUID unitId) {
        return communityUnitRepository
                .findByUnitId(unitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + unitId));
    }
}
