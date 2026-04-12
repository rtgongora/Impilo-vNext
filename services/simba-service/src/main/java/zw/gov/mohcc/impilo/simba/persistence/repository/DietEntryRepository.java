package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.DietEntryEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DietEntryRepository extends JpaRepository<DietEntryEntity, Long> {

    List<DietEntryEntity> findByTenantIdAndPersonCpidOrderByRecordedAtDesc(UUID tenantId, String personCpid);
}
