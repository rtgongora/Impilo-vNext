package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PrivilegeApprovalEntity;

import java.util.List;

@Repository
public interface PrivilegeApprovalRepository extends JpaRepository<PrivilegeApprovalEntity, Long> {

    List<PrivilegeApprovalEntity> findByPrivilegeIdOrderByCreatedAtDesc(Long privilegeId);
}
