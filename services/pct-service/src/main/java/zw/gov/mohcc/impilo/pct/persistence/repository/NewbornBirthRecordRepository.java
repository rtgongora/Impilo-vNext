package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.NewbornBirthRecordEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewbornBirthRecordRepository extends JpaRepository<NewbornBirthRecordEntity, UUID> {

    Optional<NewbornBirthRecordEntity> findByTenantIdAndSubjectCpid(UUID tenantId, String subjectCpid);

    /** Siblings from the same delivery, and any earlier children, linked through the mother. */
    List<NewbornBirthRecordEntity> findByTenantIdAndMotherCpidOrderByBornAtDesc(UUID tenantId, String motherCpid);
}
