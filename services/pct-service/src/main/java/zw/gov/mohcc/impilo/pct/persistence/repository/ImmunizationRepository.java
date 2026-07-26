package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImmunizationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImmunizationRepository extends JpaRepository<ImmunizationEntity, UUID> {

    Page<ImmunizationEntity> findByTenantIdAndSubjectCpidOrderByOccurrenceAtDesc(
            UUID tenantId, String subjectCpid, Pageable pageable);

    List<ImmunizationEntity> findByTenantIdAndSubjectCpidOrderByOccurrenceAtAsc(UUID tenantId, String subjectCpid);
}
