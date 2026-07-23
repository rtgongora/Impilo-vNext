package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.AllergyEntity;

import java.util.List;
import java.util.UUID;

public interface AllergyRepository extends JpaRepository<AllergyEntity, UUID> {

    List<AllergyEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);

    List<AllergyEntity> findByTenantIdAndSubjectCpidAndClinicalStatusOrderByCreatedAtDesc(
            UUID tenantId, String subjectCpid, String clinicalStatus);
}
