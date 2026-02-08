package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.IdempotencyEntity;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, String> {
}
