package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.FamilyHistoryMemberEntity;

import java.util.List;
import java.util.UUID;

public interface FamilyHistoryMemberRepository extends JpaRepository<FamilyHistoryMemberEntity, UUID> {

    List<FamilyHistoryMemberEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
