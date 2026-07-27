package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TopProcedureEntity;

import java.util.UUID;

public interface TopProcedureRepository extends JpaRepository<TopProcedureEntity, UUID> {
}
