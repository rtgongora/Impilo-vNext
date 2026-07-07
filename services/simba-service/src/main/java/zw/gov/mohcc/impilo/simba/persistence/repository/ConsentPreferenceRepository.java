package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.ConsentPreferenceEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentPreferenceRepository extends JpaRepository<ConsentPreferenceEntity, Long> {

    Optional<ConsentPreferenceEntity> findByTenantIdAndPersonCpid(UUID tenantId, String personCpid);
}
