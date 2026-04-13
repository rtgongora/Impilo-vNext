package zw.gov.mohcc.impilo.hrpayroll.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.PayslipEntity;

import java.util.List;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<PayslipEntity, UUID> {
    List<PayslipEntity> findByRunId(UUID runId);
}
