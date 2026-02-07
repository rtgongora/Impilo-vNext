package zw.gov.mohcc.impilo.docstore.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.docstore.persistence.entity.ObjectEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObjectRepository extends JpaRepository<ObjectEntity, Long> {

    Optional<ObjectEntity> findByObjectIdAndDeletedAtIsNull(UUID objectId);

    Optional<ObjectEntity> findByObjectId(UUID objectId);

    List<ObjectEntity> findByHashSha256AndDeletedAtIsNull(String hashSha256);

    List<ObjectEntity> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

    List<ObjectEntity> findByScanStatusAndDeletedAtIsNull(String scanStatus);
}
