package zw.gov.mohcc.impilo.community.core;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.community.api.dto.CreateChwAssignmentRequest;
import zw.gov.mohcc.impilo.community.persistence.entity.ChwAssignmentEntity;
import zw.gov.mohcc.impilo.community.persistence.repository.ChwAssignmentRepository;
import zw.gov.mohcc.impilo.community.persistence.repository.CommunityUnitRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ChwAssignmentService {

    private final ChwAssignmentRepository chwAssignmentRepository;
    private final CommunityUnitRepository communityUnitRepository;

    public ChwAssignmentService(
            ChwAssignmentRepository chwAssignmentRepository,
            CommunityUnitRepository communityUnitRepository) {
        this.chwAssignmentRepository = chwAssignmentRepository;
        this.communityUnitRepository = communityUnitRepository;
    }

    @Transactional
    public ChwAssignmentEntity createAssignment(CreateChwAssignmentRequest request) {
        if (communityUnitRepository.findByUnitId(request.unitId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown unit: " + request.unitId());
        }
        ChwAssignmentEntity entity = new ChwAssignmentEntity();
        entity.setTenantId(request.tenantId());
        entity.setUnitId(request.unitId());
        entity.setProviderId(request.providerId());
        entity.setRole(request.role() != null && !request.role().isBlank() ? request.role() : "CHW");
        entity.setStatus("ACTIVE");
        return chwAssignmentRepository.save(entity);
    }

    public List<ChwAssignmentEntity> listAssignments(UUID tenantId, UUID unitId) {
        if (unitId != null) {
            return chwAssignmentRepository.findByUnitIdOrderByAssignedAtDesc(unitId);
        }
        if (tenantId != null) {
            return chwAssignmentRepository.findByTenantIdOrderByAssignedAtDesc(tenantId);
        }
        return chwAssignmentRepository.findAll();
    }
}
