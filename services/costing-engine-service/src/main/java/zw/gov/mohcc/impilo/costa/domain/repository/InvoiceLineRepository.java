package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.InvoiceLineEntity;

import java.util.List;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLineEntity, String> {

    List<InvoiceLineEntity> findByInvoiceIdOrderByCreatedAtAsc(String invoiceId);
}
