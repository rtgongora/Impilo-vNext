package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.WriteOffEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface WriteOffRepository extends JpaRepository<WriteOffEntity, Long> {

    List<WriteOffEntity> findByTenantIdAndFacilityIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, UUID facilityId, String status);
}
