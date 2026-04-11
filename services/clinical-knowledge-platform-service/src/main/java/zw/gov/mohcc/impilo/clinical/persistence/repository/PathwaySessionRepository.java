package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.clinical.persistence.entity.PathwaySessionEntity;

import java.util.UUID;

public interface PathwaySessionRepository extends JpaRepository<PathwaySessionEntity, UUID> {
}
