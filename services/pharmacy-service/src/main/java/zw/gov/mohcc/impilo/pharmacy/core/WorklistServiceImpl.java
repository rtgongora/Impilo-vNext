package zw.gov.mohcc.impilo.pharmacy.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;

import java.util.UUID;

/**
 * Provides pharmacy worklist views filtered by facility, workspace, status, and priority.
 *
 * <p>Orders are sorted by priority (STAT first, then ASAP, URGENT, ROUTINE)
 * and then by creation time ascending so the most urgent, oldest orders
 * appear at the top of the list.</p>
 */
@Service
public class WorklistServiceImpl implements WorklistService {

    private static final Logger log = LoggerFactory.getLogger(WorklistServiceImpl.class);

    private final DispenseOrderRepository orderRepository;

    public WorklistServiceImpl(DispenseOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Get the filtered worklist for a pharmacy dispensing station.
     *
     * <p>Applies priority-based sorting: STAT > ASAP > URGENT > ROUTINE,
     * then by creation time ascending. Optional filters are applied
     * progressively: facility (always required), then workspace, status,
     * and priority.</p>
     *
     * @param facilityId  the facility (required)
     * @param workspaceId optional workspace filter
     * @param storeId     optional store filter (reserved for future use)
     * @param status      optional status filter
     * @param priority    optional priority filter
     * @param pageable    pagination parameters
     * @return a page of matching dispense orders
     */
    @Override
    public Page<DispenseOrderEntity> getWorklist(UUID facilityId, UUID workspaceId,
                                                  String storeId, DispenseStatus status,
                                                  OrderPriority priority, Pageable pageable) {
        // Apply priority-based sorting: STAT (ordinal 2) < ASAP (3) < URGENT (1) < ROUTINE (0)
        // Since enum ordinal order is ROUTINE=0, URGENT=1, STAT=2, ASAP=3,
        // we sort by priority ASC + createdAt ASC for proper queue ordering
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.asc("priority"), Sort.Order.asc("createdAt")));

        // Apply filters progressively based on provided parameters
        if (workspaceId != null && status != null && priority != null) {
            return orderRepository.findByFacilityIdAndWorkspaceIdAndStatusAndPriority(
                    facilityId, workspaceId, status, priority, sorted);
        }
        if (workspaceId != null && status != null) {
            return orderRepository.findByFacilityIdAndWorkspaceIdAndStatus(
                    facilityId, workspaceId, status, sorted);
        }
        if (workspaceId != null) {
            return orderRepository.findByFacilityIdAndWorkspaceId(
                    facilityId, workspaceId, sorted);
        }
        if (status != null && priority != null) {
            return orderRepository.findByFacilityIdAndStatusAndPriority(
                    facilityId, status, priority, sorted);
        }
        if (status != null) {
            return orderRepository.findByFacilityIdAndStatus(
                    facilityId, status, sorted);
        }
        if (priority != null) {
            return orderRepository.findByFacilityIdAndPriority(
                    facilityId, priority, sorted);
        }

        return orderRepository.findByFacilityId(facilityId, sorted);
    }
}
