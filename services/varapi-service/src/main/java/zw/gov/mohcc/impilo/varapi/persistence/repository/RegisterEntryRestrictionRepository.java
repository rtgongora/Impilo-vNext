package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.RegisterEntryRestrictionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegisterEntryRestrictionRepository extends JpaRepository<RegisterEntryRestrictionEntity, Long> {
    List<RegisterEntryRestrictionEntity> findByTenantIdAndRegistrationRecordIdInAndStatus(
            UUID tenantId, List<Long> registrationRecordIds, String status);
}
