package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffLibraryEntity;

import java.util.Optional;

@Repository
public interface TariffLibraryRepository extends JpaRepository<TariffLibraryEntity, Long> {

    Optional<TariffLibraryEntity> findByCode(String code);
}
