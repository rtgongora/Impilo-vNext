package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Read-only lookup of HSC employment records by EC number (IATG WS-D, Channel B).
 *
 * <p>Kept as a dedicated repository so the WS-D slice adds no methods to the
 * shared {@link HscEmploymentRepository}. Multiple rows for one EC number are
 * a legitimate result — the match service surfaces them as CONFLICT.</p>
 */
public interface HscEmploymentEcLookupRepository extends Repository<HscEmploymentEntity, UUID> {

    List<HscEmploymentEntity> findByTenantIdAndEcNumberOrderByUpdatedAtDesc(UUID tenantId, String ecNumber);
}
