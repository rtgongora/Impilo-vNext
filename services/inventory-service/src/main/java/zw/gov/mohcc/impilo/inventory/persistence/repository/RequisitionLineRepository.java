package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RequisitionLineEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequisitionLineRepository extends JpaRepository<RequisitionLineEntity, UUID> {

    List<RequisitionLineEntity> findByReqId(UUID reqId);
}
