package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.ProofEntity;

import java.util.List;

@Repository
public interface ProofRepository extends JpaRepository<ProofEntity, String> {
    List<ProofEntity> findByPlanIdOrderByCreatedAtDesc(String planId);
    List<ProofEntity> findByHandoffIdOrderByCreatedAtDesc(String handoffId);
}

