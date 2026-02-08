package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BillPartyEntity;

import java.util.List;

@Repository
public interface BillPartyRepository extends JpaRepository<BillPartyEntity, Long> {
    List<BillPartyEntity> findByBillId(String billId);
    void deleteByBillId(String billId);
}
