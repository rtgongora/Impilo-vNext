package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.VendorDocumentEntity;

import java.util.List;

@Repository
public interface VendorDocumentRepository extends JpaRepository<VendorDocumentEntity, String> {
    List<VendorDocumentEntity> findByVendorId(String vendorId);
}
