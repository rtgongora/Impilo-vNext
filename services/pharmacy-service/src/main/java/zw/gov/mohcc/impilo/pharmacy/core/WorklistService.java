package zw.gov.mohcc.impilo.pharmacy.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;

import java.util.UUID;

/**
 * Service providing pharmacy worklist views with filtering.
 *
 * <p>Returns paginated lists of dispense orders filtered by
 * facility, workspace, store, status, and priority.</p>
 */
public interface WorklistService {

    /**
     * Get the pharmacy worklist filtered by optional parameters.
     */
    Page<DispenseOrderEntity> getWorklist(UUID facilityId, UUID workspaceId,
                                           String storeId, DispenseStatus status,
                                           OrderPriority priority, Pageable pageable);
}
