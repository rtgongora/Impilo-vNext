package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.CareLinkageEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CareLinkageRepository extends JpaRepository<CareLinkageEntity, Long> {

    List<CareLinkageEntity> findByTenantIdAndPersonCpidOrderByCreatedAtDesc(UUID tenantId, String personCpid);
}
