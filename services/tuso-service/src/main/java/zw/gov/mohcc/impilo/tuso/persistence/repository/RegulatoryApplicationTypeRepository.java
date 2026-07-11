package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryApplicationTypeEntity;

import java.util.List;

public interface RegulatoryApplicationTypeRepository extends JpaRepository<RegulatoryApplicationTypeEntity, String> {
    List<RegulatoryApplicationTypeEntity> findByActiveTrue();
}
