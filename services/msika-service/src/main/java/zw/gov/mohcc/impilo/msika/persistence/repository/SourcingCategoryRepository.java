package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msika.persistence.entity.SourcingCategoryEntity;

import java.util.List;

@Repository
public interface SourcingCategoryRepository extends JpaRepository<SourcingCategoryEntity, String> {

    List<SourcingCategoryEntity> findByStatusOrderByLabelAsc(String status);
}
