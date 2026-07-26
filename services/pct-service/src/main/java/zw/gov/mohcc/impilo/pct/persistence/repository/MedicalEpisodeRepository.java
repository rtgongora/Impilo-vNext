package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicalEpisodeEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MedicalEpisodeRepository extends JpaRepository<MedicalEpisodeEntity, UUID> {

    List<MedicalEpisodeEntity> findByTenantIdAndSubjectCpidOrderByStartedOnDesc(
            UUID tenantId, String subjectCpid);

    List<MedicalEpisodeEntity> findByTenantIdAndSubjectCpidAndStatusInOrderByStartedOnDesc(
            UUID tenantId, String subjectCpid, Collection<String> statuses);
}
