package zw.gov.mohcc.impilo.mushex.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.api.dto.FinancialPlatformCardRequests;
import zw.gov.mohcc.impilo.mushex.domain.entity.CardProfileEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ReversalRecordEntity;
import zw.gov.mohcc.impilo.mushex.domain.repository.CardProfileRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ReversalRecordRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@Service
public class FinancialCardReversalPlatformService {

    private final CardProfileRepository cardProfileRepository;
    private final ReversalRecordRepository reversalRecordRepository;

    public FinancialCardReversalPlatformService(CardProfileRepository cardProfileRepository,
                                                ReversalRecordRepository reversalRecordRepository) {
        this.cardProfileRepository = cardProfileRepository;
        this.reversalRecordRepository = reversalRecordRepository;
    }

    public List<CardProfileEntity> listCardProfiles(String walletId) {
        var ctx = TrustContextHolder.require();
        if (walletId != null && !walletId.isBlank()) {
            return cardProfileRepository.findByTenantIdAndWalletIdOrderByIssuedAtDesc(ctx.tenantId(), walletId);
        }
        return cardProfileRepository.findByTenantIdOrderByIssuedAtDesc(ctx.tenantId());
    }

    @Transactional
    public CardProfileEntity createCardProfile(FinancialPlatformCardRequests.CreateCardProfileRequest req) {
        var ctx = TrustContextHolder.require();
        CardProfileEntity e = new CardProfileEntity();
        e.setCardProfileId(UlidGenerator.generate());
        e.setTenantId(ctx.tenantId());
        e.setWalletId(req.walletId());
        e.setOwnerRef(req.ownerRef());
        e.setCardType(req.cardType());
        e.setTokenRef(req.tokenRef());
        e.setCardStatus("ISSUED");
        e.setExpiryMeta(req.expiryMetaJson() != null ? req.expiryMetaJson() : "{}");
        e.setMetadata(req.metadataJson() != null ? req.metadataJson() : "{}");
        return cardProfileRepository.save(e);
    }

    public List<ReversalRecordEntity> listReversals() {
        var ctx = TrustContextHolder.require();
        return reversalRecordRepository.findByTenantIdOrderByCreatedAtDesc(ctx.tenantId());
    }

    @Transactional
    public ReversalRecordEntity createReversal(FinancialPlatformCardRequests.CreateReversalRecordRequest req) {
        var ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        if (reversalRecordRepository.existsByTenantIdAndReversalCode(tenantId, req.reversalCode())) {
            throw new IllegalStateException("Reversal code already exists: " + req.reversalCode());
        }
        ReversalRecordEntity e = new ReversalRecordEntity();
        e.setReversalId(UlidGenerator.generate());
        e.setTenantId(tenantId);
        e.setReversalCode(req.reversalCode());
        e.setOriginalTxnRef(req.originalTxnRef());
        e.setAmount(req.amount());
        e.setCurrency(req.currency() != null ? req.currency() : "USD");
        e.setReason(req.reason());
        e.setReversalStatus("INITIATED");
        e.setMetadata(req.metadataJson() != null ? req.metadataJson() : "{}");
        return reversalRecordRepository.save(e);
    }
}
