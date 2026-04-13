package zw.gov.mohcc.impilo.hrpayroll.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.hrpayroll.persistence.entity.ContractEntity;

import java.util.List;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<ContractEntity, UUID> {
    List<ContractEntity> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);
}
