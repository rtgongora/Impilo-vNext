package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.ClaimPackEntity;

import java.util.List;

@Repository
public interface ClaimPackRepository extends JpaRepository<ClaimPackEntity, Long> {
    List<ClaimPackEntity> findByBillId(String billId);
}
