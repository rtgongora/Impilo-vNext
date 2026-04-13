package zw.gov.mohcc.impilo.procurement.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procurement.persistence.entity.RequisitionLineEntity;

import java.util.List;
import java.util.UUID;

public interface RequisitionLineRepository extends JpaRepository<RequisitionLineEntity, UUID> {
    List<RequisitionLineEntity> findByRequisitionId(UUID requisitionId);
}
