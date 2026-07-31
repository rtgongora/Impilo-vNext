package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.CareTransferEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ConsultationEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.CareTransferRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ConsultationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transfer of care (brief.md §14, §23.10).
 *
 * <p><strong>The one property this service exists to hold: ownership moves only on acceptance.</strong>
 * A consultation may recommend a takeover ({@code takeover_recommended}); requesting a transfer
 * here does not perform one either. The receiving service must write back with its own record id
 * ({@code accepting_ref}). That write — and only that write — updates
 * {@code pct_consultations.owning_service} when a consultation is linked.</p>
 *
 * <p>{@link ConsultationService#answer} deliberately cannot change ownership. This service is the
 * separate act V114's comments promised. Do not fold acceptance into {@code answer}.</p>
 */
@Service
public class CareTransferService {

    private static final Logger log = LoggerFactory.getLogger(CareTransferService.class);

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String WITHDRAWN = "WITHDRAWN";

    private final CareTransferRepository transferRepository;
    private final ConsultationRepository consultationRepository;
    private final ClinicalAccessGuard accessGuard;

    public CareTransferService(CareTransferRepository transferRepository,
                               ConsultationRepository consultationRepository,
                               ClinicalAccessGuard accessGuard) {
        this.transferRepository = transferRepository;
        this.consultationRepository = consultationRepository;
        this.accessGuard = accessGuard;
    }

    /**
     * Request a transfer. Does <strong>not</strong> change ownership — the patient stays with
     * {@code from_service} until the receiving service accepts.
     */
    @Transactional
    public CareTransferEntity request(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String subjectCpid = required(body, "subject_cpid", "subjectCpid");
        String journeyRaw = str(body.get("journey_id"), body.get("journeyId"));
        String encounterRaw = str(body.get("encounter_id"), body.get("encounterId"));
        accessGuard.requireCareRelationship(ctx, subjectCpid, journeyRaw, encounterRaw);

        if (journeyRaw == null && encounterRaw == null) {
            throw new IllegalArgumentException(
                    "A care transfer must carry a journey_id or an encounter_id "
                    + "(care-continuum-doctrine CC-5).");
        }

        String fromService = required(body, "from_service", "fromService");
        String toService = required(body, "to_service", "toService");
        if (fromService.equalsIgnoreCase(toService)) {
            throw new IllegalArgumentException(
                    "A service cannot transfer care to itself: from and to are both " + toService + ".");
        }

        String reason = str(body.get("reason"));
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Requesting a transfer requires a reason — an empty request is indistinguishable "
                    + "from a silent assumption that the other team already holds the patient.");
        }

        transferRepository.findByTenantIdAndSubjectCpidAndToServiceAndStatus(
                ctx.tenantId(), subjectCpid, toService, PENDING).ifPresent(existing -> {
            throw new IllegalStateException(
                    "Subject " + subjectCpid + " already has a pending transfer to " + toService
                    + " (" + existing.getTransferId() + ") — decline or withdraw it before raising another.");
        });

        UUID consultationId = uuid(str(body.get("consultation_id"), body.get("consultationId")));
        if (consultationId != null) {
            ConsultationEntity consultation = consultationRepository
                    .findByTenantIdAndConsultationId(ctx.tenantId(), consultationId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Consultation not found: " + consultationId));
            if (!subjectCpid.equals(consultation.getSubjectCpid())) {
                throw new IllegalArgumentException(
                        "Consultation " + consultationId + " is for a different subject than this transfer.");
            }
            transferRepository.findByTenantIdAndConsultationIdAndStatus(
                    ctx.tenantId(), consultationId, PENDING).ifPresent(existing -> {
                throw new IllegalStateException(
                        "Consultation " + consultationId + " already has a pending transfer ("
                        + existing.getTransferId() + ").");
            });
        }

        CareTransferEntity e = new CareTransferEntity();
        e.setTransferId(UUID.randomUUID());
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(subjectCpid);
        e.setJourneyId(uuid(journeyRaw));
        e.setEncounterId(uuid(encounterRaw));
        e.setConsultationId(consultationId);
        e.setFromService(fromService);
        e.setToService(toService);
        e.setRequestedBy(ctx.actorId());
        e.setReason(reason);
        e.setStatus(PENDING);

        transferRepository.save(e);
        log.info("pct.care_transfer.requested id={} subject={} from={} to={} consultation={} by={} correlationId={}",
                e.getTransferId(), subjectCpid, fromService, toService, consultationId,
                ctx.actorId(), ctx.correlationId());
        return e;
    }

    /**
     * Accept a transfer. The caller must supply {@code accepting_ref} — the receiving service's OWN
     * record id. This is the one write that moves ownership on a linked consultation.
     */
    @Transactional
    public CareTransferEntity accept(UUID transferId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        CareTransferEntity e = require(transferId, ctx);

        if (!PENDING.equals(e.getStatus())) {
            throw new IllegalArgumentException(
                    "Transfer " + transferId + " is " + e.getStatus() + " and cannot be accepted.");
        }

        String acceptingRef = str(body.get("accepting_ref"), body.get("acceptingRef"));
        if (acceptingRef == null || acceptingRef.isBlank()) {
            throw new IllegalArgumentException(
                    "accepting_ref is required — the receiving service's own record id is the "
                    + "handshake itself. Without it, acceptance would move ownership on an opinion.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        e.setStatus(ACCEPTED);
        e.setAcceptedBy(ctx.actorId());
        e.setAcceptedAt(now);
        e.setAcceptingRef(acceptingRef.trim());
        transferRepository.save(e);

        // THE ownership move. Deliberately not in ConsultationService.answer — that path's guard
        // still throws if ownership ever changes there. This is the separate act.
        if (e.getConsultationId() != null) {
            ConsultationEntity consultation = consultationRepository
                    .findByTenantIdAndConsultationId(ctx.tenantId(), e.getConsultationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Transfer " + transferId + " links consultation " + e.getConsultationId()
                            + " which no longer exists."));
            String ownerBefore = consultation.getOwningService();
            consultation.setOwningService(e.getToService());
            consultationRepository.save(consultation);
            log.info("pct.care_transfer.ownership_moved consultation={} from={} to={} transfer={} acceptingRef={} correlationId={}",
                    e.getConsultationId(), ownerBefore, e.getToService(), transferId, acceptingRef,
                    ctx.correlationId());
        }

        log.info("pct.care_transfer.accepted id={} by={} acceptingRef={} correlationId={}",
                transferId, ctx.actorId(), acceptingRef, ctx.correlationId());
        return e;
    }

    @Transactional
    public CareTransferEntity decline(UUID transferId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        CareTransferEntity e = require(transferId, ctx);
        if (!PENDING.equals(e.getStatus())) {
            throw new IllegalArgumentException(
                    "Transfer " + transferId + " is " + e.getStatus() + " and cannot be declined.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Declining a transfer requires a reason, so the requester knows whether to ask "
                    + "again, ask someone else, or keep the patient.");
        }
        e.setStatus(DECLINED);
        e.setDeclinedBy(ctx.actorId());
        e.setDeclinedAt(OffsetDateTime.now());
        e.setDeclineReason(reason);
        CareTransferEntity saved = transferRepository.save(e);
        log.info("pct.care_transfer.declined id={} by={} correlationId={}",
                transferId, ctx.actorId(), ctx.correlationId());
        return saved;
    }

    @Transactional
    public CareTransferEntity withdraw(UUID transferId) {
        TrustContext ctx = TrustContextHolder.require();
        CareTransferEntity e = require(transferId, ctx);
        if (!PENDING.equals(e.getStatus())) {
            throw new IllegalArgumentException(
                    "Transfer " + transferId + " is " + e.getStatus() + " and cannot be withdrawn.");
        }
        e.setStatus(WITHDRAWN);
        e.setWithdrawnBy(ctx.actorId());
        e.setWithdrawnAt(OffsetDateTime.now());
        return transferRepository.save(e);
    }

    @Transactional(readOnly = true)
    public List<CareTransferEntity> listForSubject(String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        return transferRepository.findByTenantIdAndSubjectCpidOrderByRequestedAtDesc(
                ctx.tenantId(), subjectCpid);
    }

    @Transactional(readOnly = true)
    public List<CareTransferEntity> inbox(String toService) {
        TrustContext ctx = TrustContextHolder.require();
        return transferRepository.findByTenantIdAndToServiceAndStatusOrderByRequestedAtAsc(
                ctx.tenantId(), toService, PENDING);
    }

    private CareTransferEntity require(UUID id, TrustContext ctx) {
        return transferRepository.findByTenantIdAndTransferId(ctx.tenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("Care transfer not found: " + id));
    }

    private static String required(Map<String, Object> body, String... keys) {
        String v = str(keys.length == 0 ? null : body.get(keys[0]),
                keys.length > 1 ? body.get(keys[1]) : null);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(keys[0] + " is required");
        }
        return v;
    }

    private static String str(Object... values) {
        for (Object v : values) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
        }
        return null;
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return UUID.fromString(raw);
    }
}
