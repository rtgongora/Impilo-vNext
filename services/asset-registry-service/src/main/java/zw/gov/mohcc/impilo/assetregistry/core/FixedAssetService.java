package zw.gov.mohcc.impilo.assetregistry.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetDepreciationScheduleEntity;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetFixedDetailEntity;
import zw.gov.mohcc.impilo.assetregistry.repository.AssetDepreciationScheduleRepository;
import zw.gov.mohcc.impilo.assetregistry.repository.AssetFixedDetailRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class FixedAssetService {

    private final AssetFixedDetailRepository detailRepository;
    private final AssetDepreciationScheduleRepository scheduleRepository;

    public FixedAssetService(AssetFixedDetailRepository detailRepository,
                             AssetDepreciationScheduleRepository scheduleRepository) {
        this.detailRepository = detailRepository;
        this.scheduleRepository = scheduleRepository;
    }

    public AssetFixedDetailEntity getOrNull(UUID assetId) {
        return detailRepository.findByAssetId(assetId).orElse(null);
    }

    @Transactional
    public AssetFixedDetailEntity upsert(AssetFixedDetailEntity detail) {
        return detailRepository.findByAssetId(detail.getAssetId()).map(existing -> {
            if (detail.getAcquisitionDate() != null) {
                existing.setAcquisitionDate(detail.getAcquisitionDate());
            }
            if (detail.getAcquisitionCost() != null) {
                existing.setAcquisitionCost(detail.getAcquisitionCost());
            }
            if (detail.getUsefulLifeMonths() != null) {
                existing.setUsefulLifeMonths(detail.getUsefulLifeMonths());
            }
            if (detail.getSalvageValue() != null) {
                existing.setSalvageValue(detail.getSalvageValue());
            }
            if (detail.getDepreciationMethod() != null) {
                existing.setDepreciationMethod(detail.getDepreciationMethod());
            }
            if (detail.getGlAssetAccount() != null) {
                existing.setGlAssetAccount(detail.getGlAssetAccount());
            }
            if (detail.getGlDepreciationAccount() != null) {
                existing.setGlDepreciationAccount(detail.getGlDepreciationAccount());
            }
            if (detail.getGlExpenseAccount() != null) {
                existing.setGlExpenseAccount(detail.getGlExpenseAccount());
            }
            BigDecimal acc = existing.getAccumulatedDepreciation() != null ? existing.getAccumulatedDepreciation() : BigDecimal.ZERO;
            if (existing.getAcquisitionCost() != null) {
                existing.setNetBookValue(existing.getAcquisitionCost().subtract(acc));
            }
            return detailRepository.save(existing);
        }).orElseGet(() -> {
            if (detail.getAccumulatedDepreciation() == null) {
                detail.setAccumulatedDepreciation(BigDecimal.ZERO);
            }
            if (detail.getNetBookValue() == null && detail.getAcquisitionCost() != null) {
                detail.setNetBookValue(detail.getAcquisitionCost().subtract(detail.getAccumulatedDepreciation()));
            }
            return detailRepository.save(detail);
        });
    }

    /**
     * Straight-line monthly depreciation for one period.
     */
    @Transactional
    public AssetDepreciationScheduleEntity runMonthlyDepreciation(UUID assetId, LocalDate periodDate) {
        AssetFixedDetailEntity d = detailRepository.findByAssetId(assetId).orElseThrow();
        if (!"STRAIGHT_LINE".equals(d.getDepreciationMethod())) {
            throw new IllegalStateException("Only STRAIGHT_LINE supported in batch");
        }
        BigDecimal cost = d.getAcquisitionCost() != null ? d.getAcquisitionCost() : BigDecimal.ZERO;
        BigDecimal salvage = d.getSalvageValue() != null ? d.getSalvageValue() : BigDecimal.ZERO;
        int life = d.getUsefulLifeMonths() != null && d.getUsefulLifeMonths() > 0 ? d.getUsefulLifeMonths() : 1;
        BigDecimal monthly = cost.subtract(salvage).divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP);
        BigDecimal acc = d.getAccumulatedDepreciation().add(monthly);
        BigDecimal nbv = cost.subtract(acc);
        if (nbv.compareTo(salvage) < 0) {
            monthly = monthly.add(nbv.subtract(salvage));
            nbv = salvage;
            acc = cost.subtract(salvage);
        }
        d.setAccumulatedDepreciation(acc);
        d.setNetBookValue(nbv);
        detailRepository.save(d);
        AssetDepreciationScheduleEntity row = new AssetDepreciationScheduleEntity();
        row.setAssetId(assetId);
        row.setPeriodDate(periodDate);
        row.setDepreciationAmount(monthly);
        row.setAccumulated(acc);
        row.setNetBookValue(nbv);
        row.setPostedToGl(false);
        return scheduleRepository.save(row);
    }

    @Transactional
    public AssetFixedDetailEntity recordDisposal(UUID assetId, LocalDate disposalDate, BigDecimal amount, String reason) {
        AssetFixedDetailEntity d = detailRepository.findByAssetId(assetId).orElseThrow();
        d.setDisposalDate(disposalDate);
        d.setDisposalAmount(amount);
        d.setDisposalReason(reason);
        return detailRepository.save(d);
    }

    public List<AssetDepreciationScheduleEntity> schedule(UUID assetId) {
        return scheduleRepository.findByAssetIdOrderByPeriodDateAsc(assetId);
    }
}
