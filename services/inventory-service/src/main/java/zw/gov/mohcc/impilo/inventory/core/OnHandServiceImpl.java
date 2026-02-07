package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.OnHandRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only service over the on-hand stock projection.
 *
 * <p>On-hand rows are maintained by the {@link LedgerServiceImpl} as a
 * denormalized projection. This service provides filtered, paginated views
 * for dashboards, stock alerts, and operational queries.</p>
 */
@Service
public class OnHandServiceImpl implements OnHandService {

    private static final Logger log = LoggerFactory.getLogger(OnHandServiceImpl.class);

    private final OnHandRepository onHandRepository;

    public OnHandServiceImpl(OnHandRepository onHandRepository) {
        this.onHandRepository = onHandRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Applies filters hierarchically: if binId is provided, filters to that bin;
     * else if itemCode is provided with storeId, filters to that item/store combination;
     * else if storeId is provided, filters to that store; else returns all positions
     * for the facility.</p>
     */
    @Override
    public Page<OnHandEntity> getOnHand(UUID facilityId, UUID storeId, UUID binId,
                                          String itemCode, Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        if (binId != null) {
            log.debug("Querying on-hand: tenant={}, facility={}, store={}, bin={}",
                    tenantId, facilityId, storeId, binId);
            return onHandRepository.findByTenantIdAndFacilityIdAndStoreIdAndBinId(
                    tenantId, facilityId, storeId, binId, pageable);
        }

        if (itemCode != null && !itemCode.isBlank() && storeId != null) {
            log.debug("Querying on-hand: tenant={}, facility={}, store={}, item={}",
                    tenantId, facilityId, storeId, itemCode);
            return onHandRepository.findByTenantIdAndFacilityIdAndStoreIdAndItemCode(
                    tenantId, facilityId, storeId, itemCode, pageable);
        }

        if (storeId != null) {
            log.debug("Querying on-hand: tenant={}, facility={}, store={}",
                    tenantId, facilityId, storeId);
            return onHandRepository.findByTenantIdAndFacilityIdAndStoreId(
                    tenantId, facilityId, storeId, pageable);
        }

        log.debug("Querying on-hand: tenant={}, facility={}", tenantId, facilityId);
        return onHandRepository.findByTenantIdAndFacilityId(tenantId, facilityId, pageable);
    }

    @Override
    public List<OnHandEntity> getNearExpiry(UUID facilityId, int daysThreshold) {
        TrustContext ctx = TrustContextHolder.require();
        log.debug("Querying near-expiry: facility={}, daysThreshold={}", facilityId, daysThreshold);
        LocalDate threshold = LocalDate.now().plusDays(daysThreshold);
        return onHandRepository.findNearExpiry(ctx.tenantId(), facilityId, threshold);
    }

    @Override
    public List<OnHandEntity> getStockouts(UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        log.debug("Querying stockouts: facility={}", facilityId);
        return onHandRepository.findStockouts(ctx.tenantId(), facilityId);
    }

    @Override
    public OnHandEntity getPosition(UUID facilityId, UUID storeId, String itemCode,
                                      String batch, LocalDate expiry) {
        log.debug("Querying position: facility={}, store={}, item={}, batch={}, expiry={}",
                facilityId, storeId, itemCode, batch, expiry);
        return onHandRepository.findByFacilityIdAndStoreIdAndItemCodeAndBatchAndExpiry(
                facilityId, storeId, itemCode, batch, expiry)
                .orElse(null);
    }
}
