package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.LocalityProposalEntity;

import java.util.List;

public interface LocalityProposalRepository extends JpaRepository<LocalityProposalEntity, Long> {

    List<LocalityProposalEntity> findByStatusOrderByCreatedAtAsc(String status);
}
