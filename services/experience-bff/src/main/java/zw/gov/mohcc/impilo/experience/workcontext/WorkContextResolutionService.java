package zw.gov.mohcc.impilo.experience.workcontext;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unions the six authoritative work-context sources into one discriminated,
 * ranked, deduplicated list — the resolver every landing decision and duty-
 * token mint is built on (Phase C).
 *
 * <p>Composes, never copies: every {@link ResolvedWorkContext} retains
 * {@code sourceSystem}/{@code sourceRecordId}, and no shadow table stores a
 * resolved context as if it were itself authoritative — {@link #resolve}
 * re-fetches from source on every call, and {@code proveContext} (session
 * mint) re-fetches again rather than trusting a cached picker result.</p>
 */
@Service
public class WorkContextResolutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkContextResolutionService.class);

    private final VarapiServiceClient varapiClient;
    private final VashandiContextSource vashandiSource;
    private final GovernanceContextSource governanceSource;
    private final RegulatoryContextSource regulatorySource;
    private final FacilityAdminContextSource facilityAdminSource;
    private final SupportContextSource supportSource;

    public WorkContextResolutionService(VarapiServiceClient varapiClient,
                                         VashandiContextSource vashandiSource,
                                         GovernanceContextSource governanceSource,
                                         RegulatoryContextSource regulatorySource,
                                         FacilityAdminContextSource facilityAdminSource,
                                         SupportContextSource supportSource) {
        this.varapiClient = varapiClient;
        this.vashandiSource = vashandiSource;
        this.governanceSource = governanceSource;
        this.regulatorySource = regulatorySource;
        this.facilityAdminSource = facilityAdminSource;
        this.supportSource = supportSource;
    }

    public record ResolutionOutcome(
            List<ResolvedWorkContext> contexts,
            List<WorkContextSourceStatus> sourceStatuses,
            String recommendedContextId,
            boolean requiresContextChooser,
            String friendlyResolutionState // null | work_context_partially_available | work_context_unavailable
    ) {}

    public ResolutionOutcome resolve(String actorHealthId, String entryRouteContextKind) {
        String providerPublicId = resolveProviderPublicId(actorHealthId);

        List<WorkContextSourceStatus> statuses = new ArrayList<>();
        List<ResolvedWorkContext> raw = new ArrayList<>();

        collect(vashandiSource.resolve(actorHealthId), statuses, raw);
        collect(governanceSource.resolve(actorHealthId, providerPublicId), statuses, raw);
        collect(regulatorySource.resolve(actorHealthId), statuses, raw);
        collect(facilityAdminSource.resolve(actorHealthId), statuses, raw);
        collect(supportSource.resolve(actorHealthId), statuses, raw);

        long degradedCount = statuses.stream().filter(s -> s.state() == WorkContextSourceStatus.State.DEGRADED).count();
        boolean allDegraded = degradedCount == statuses.size() && !statuses.isEmpty();

        List<ResolvedWorkContext> merged = dedupAndMerge(raw);
        List<ResolvedWorkContext> ranked = applyRanking(merged, entryRouteContextKind, degradedCount > 0);
        ranked = WorkContextRanking.sortByScoreDesc(ranked);

        if (allDegraded) {
            return new ResolutionOutcome(List.of(), statuses, null, false, "work_context_unavailable");
        }
        if (ranked.isEmpty()) {
            return new ResolutionOutcome(List.of(), statuses, null, false, null);
        }

        boolean chooser = WorkContextRanking.requiresChooser(ranked, degradedCount > 0);
        String friendlyState = degradedCount > 0 ? "work_context_partially_available" : null;
        String recommended = ranked.get(0).contextId();

        return new ResolutionOutcome(ranked, statuses, recommended, chooser, friendlyState);
    }

    /** Re-fetch and re-validate a specific contextId from source — used by the session mint, never a cache read. */
    public ResolvedWorkContext proveContext(String actorHealthId, String entryRouteContextKind, String contextId) {
        if (contextId == null || contextId.isBlank()) {
            return null;
        }
        ResolutionOutcome outcome = resolve(actorHealthId, entryRouteContextKind);
        return outcome.contexts().stream()
                .filter(c -> contextId.equals(c.contextId()))
                .findFirst()
                .orElse(null);
    }

    private void collect(WorkContextAdapterResult result, List<WorkContextSourceStatus> statuses, List<ResolvedWorkContext> raw) {
        statuses.add(result.status());
        raw.addAll(result.contexts());
    }

    /** Same physical duty described by two sources collapses to one row; sources are unioned, never dropped. */
    private List<ResolvedWorkContext> dedupAndMerge(List<ResolvedWorkContext> raw) {
        Map<String, ResolvedWorkContext> byKey = new LinkedHashMap<>();
        for (ResolvedWorkContext ctx : raw) {
            String key = ctx.dedupKey();
            ResolvedWorkContext existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, ctx);
                continue;
            }
            byKey.put(key, mergeContexts(existing, ctx));
        }
        return new ArrayList<>(byKey.values());
    }

    private ResolvedWorkContext mergeContexts(ResolvedWorkContext a, ResolvedWorkContext b) {
        List<String> modes = new ArrayList<>(a.availableModes());
        for (String m : b.availableModes()) {
            if (!modes.contains(m)) modes.add(m);
        }
        String mergedSourceRecordId = a.sourceSystem().equals(b.sourceSystem())
                ? a.sourceRecordId()
                : a.sourceSystem() + ":" + a.sourceRecordId() + "+" + b.sourceSystem() + ":" + b.sourceRecordId();
        return new ResolvedWorkContext(
                a.contextId(), a.contextKind(), a.sourceSystem(), mergedSourceRecordId,
                a.facilityId() != null ? a.facilityId() : b.facilityId(),
                a.organisationId() != null ? a.organisationId() : b.organisationId(),
                a.jurisdictionCode() != null ? a.jurisdictionCode() : b.jurisdictionCode(),
                a.programmeId() != null ? a.programmeId() : b.programmeId(),
                a.roleTemplateId(), a.canonicalRoles(), modes,
                a.defaultMode() != null ? a.defaultMode() : b.defaultMode(),
                a.modeSource(),
                a.departmentId() != null ? a.departmentId() : b.departmentId(),
                a.departmentLabel() != null ? a.departmentLabel() : b.departmentLabel(),
                a.wardId() != null ? a.wardId() : b.wardId(),
                a.servicePointId() != null ? a.servicePointId() : b.servicePointId(),
                a.workspaceId() != null ? a.workspaceId() : b.workspaceId(),
                a.shift() != null ? a.shift() : b.shift(),
                a.restrictions(), a.label(), a.groupHint(), a.rankScore());
    }

    private List<ResolvedWorkContext> applyRanking(List<ResolvedWorkContext> contexts, String entryRouteContextKind,
                                                     boolean anyDegraded) {
        List<ResolvedWorkContext> out = new ArrayList<>();
        for (ResolvedWorkContext c : contexts) {
            WorkContextRanking.Signals signals = new WorkContextRanking.Signals(
                    entryRouteContextKind != null && entryRouteContextKind.equalsIgnoreCase(c.contextKind()),
                    c.shift() != null && "CHECKED_IN".equals(c.shift().get("checkInState")),
                    c.shift() != null && "CHECKED_IN".equals(c.shift().get("checkInState")),
                    c.rankScore() >= 250, // primary flag was pre-applied as a rank boost by GovernanceContextSource
                    false, // last-valid-context: requires client-supplied previousJti context id; wired by the caller
                    false, // shift-starting-soon: not yet surfaced by the C2 read-model at adapter level
                    c.rankScore() > 0 && c.rankScore() < 250,
                    false, // recent-pattern: requires scoped_token history read, deferred
                    !c.restrictions().isEmpty(),
                    anyDegraded);
            int score = WorkContextRanking.score(signals);
            out.add(c.withRankScore(score));
        }
        return out;
    }

    private String resolveProviderPublicId(String actorHealthId) {
        try {
            JsonNode provider = varapiClient.getProviderByHealthId(actorHealthId);
            if (provider == null || provider.isNull()) return null;
            if (provider.has("providerPublicId") && !provider.get("providerPublicId").asText("").isBlank()) {
                return provider.get("providerPublicId").asText();
            }
            if (provider.has("providerId") && !provider.get("providerId").asText("").isBlank()) {
                return provider.get("providerId").asText();
            }
            return null;
        } catch (Exception e) {
            log.debug("Provider lookup failed for {} (governance PROVIDER-subject query will be skipped): {}",
                    actorHealthId, e.getMessage());
            return null;
        }
    }
}
