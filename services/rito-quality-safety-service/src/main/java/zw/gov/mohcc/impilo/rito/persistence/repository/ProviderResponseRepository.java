package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderResponseEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderResponseRepository extends JpaRepository<ProviderResponseEntity, UUID> {

    List<ProviderResponseEntity> findByRatingId(UUID ratingId);
}
