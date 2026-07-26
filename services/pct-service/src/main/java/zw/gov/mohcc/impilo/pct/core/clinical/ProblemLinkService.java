package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemLinkEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemLinkRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Relationships from a problem to the rest of the record.
 *
 * <p>What makes a problem list clinically usable is not the list — it is knowing what a diagnosis
 * rests on, what it caused, what is treating it and what is watching it. Those are the links.</p>
 */
@Service
public class ProblemLinkService {

    private static final Logger log = LoggerFactory.getLogger(ProblemLinkService.class);

    private final ProblemLinkRepository linkRepository;
    private final ProblemRepository problemRepository;
    private final ClinicalAccessGuard accessGuard;

    public ProblemLinkService(ProblemLinkRepository linkRepository,
                              ProblemRepository problemRepository,
                              ClinicalAccessGuard accessGuard) {
        this.linkRepository = linkRepository;
        this.problemRepository = problemRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<ProblemLinkEntity> list(UUID problemId) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return linkRepository.findByTenantIdAndProblemIdOrderByCreatedAtDesc(tenantId, problemId);
    }

    @Transactional
    public ProblemLinkEntity link(UUID problemId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        ProblemEntity problem = requireProblem(problemId, ctx);
        accessGuard.requireCareRelationship(ctx, problem.getSubjectCpid(),
                problem.getJourneyId(), problem.getEncounterId());

        ProblemLinkEntity link = new ProblemLinkEntity();
        link.setTenantId(ctx.tenantId());
        link.setProblemId(problemId);
        link.setLinkType(ProblemVocabulary.require("link_type",
                str(body.get("link_type"), body.get("linkType")),
                ProblemLinkVocabulary.LINK_TYPES, true));
        link.setTargetType(ProblemVocabulary.require("target_type",
                str(body.get("target_type"), body.get("targetType")),
                ProblemLinkVocabulary.TARGET_TYPES, true));
        link.setNote(str(body.get("note")));
        link.setRecordedBy(ctx.actorId());

        String targetRef = str(body.get("target_ref"), body.get("targetRef"),
                body.get("target_problem_id"), body.get("targetProblemId"));
        if (targetRef == null) {
            throw new IllegalArgumentException("Missing field: target_ref");
        }

        if (ProblemLinkVocabulary.LOCAL_TARGET.equals(link.getTargetType())) {
            UUID targetProblemId = UUID.fromString(targetRef);
            // A problem cannot be a complication of, or caused by, itself.
            if (targetProblemId.equals(problemId)) {
                throw new IllegalArgumentException("A problem cannot be linked to itself.");
            }
            ProblemEntity target = requireProblem(targetProblemId, ctx);
            // Linking across patients would attribute one person's disease to another's.
            if (!target.getSubjectCpid().equals(problem.getSubjectCpid())) {
                throw new IllegalArgumentException(
                        "Problem " + targetProblemId + " belongs to a different patient.");
            }
            link.setTargetProblemId(targetProblemId);
        } else {
            // Not a foreign key: the object lives in another service, which validates it on read.
            link.setTargetRef(targetRef);
        }

        link = linkRepository.save(link);
        log.info("pct.problem_link.created problem={} type={} target={}:{} by={} correlationId={}",
                problemId, link.getLinkType(), link.getTargetType(),
                link.getTargetProblemId() != null ? link.getTargetProblemId() : link.getTargetRef(),
                ctx.actorId(), ctx.correlationId());
        return link;
    }

    @Transactional
    public void unlink(UUID linkId) {
        TrustContext ctx = TrustContextHolder.require();
        ProblemLinkEntity link = linkRepository.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Link not found: " + linkId));
        if (!link.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Link not found: " + linkId);
        }
        linkRepository.delete(link);
        log.info("pct.problem_link.removed id={} by={}", linkId, ctx.actorId());
    }

    private ProblemEntity requireProblem(UUID problemId, TrustContext ctx) {
        ProblemEntity p = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + problemId));
        if (!p.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Problem not found: " + problemId);
        }
        return p;
    }

    private static String str(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }
}
