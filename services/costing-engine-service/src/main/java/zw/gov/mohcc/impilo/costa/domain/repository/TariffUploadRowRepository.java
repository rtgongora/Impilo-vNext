package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffUploadRowEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface TariffUploadRowRepository extends JpaRepository<TariffUploadRowEntity, Long> {

    List<TariffUploadRowEntity> findByUploadBatchIdOrderByRowNumberAsc(UUID uploadBatchId);

    void deleteByUploadBatchId(UUID uploadBatchId);
}
