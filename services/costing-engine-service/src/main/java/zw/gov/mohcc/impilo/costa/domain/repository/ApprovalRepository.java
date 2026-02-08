package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.ApprovalEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ApprovalStatus;

import java.util.List;

@Repository
public interface ApprovalRepository extends JpaRepository<ApprovalEntity, Long> {
    List<ApprovalEntity> findByBillIdOrderByCreatedAtAsc(String billId);
    List<ApprovalEntity> findByBillIdAndStatus(String billId, ApprovalStatus status);
}
