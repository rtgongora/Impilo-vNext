package zw.gov.mohcc.impilo.experience.workcontext;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves a programme appointment's target against the WGV programme registry
 * ({@code wgv_programme}, Phase A2).
 *
 * <p>Why this exists: a programme work context is a {@code wgv_assignment} row with
 * {@code target_type=PROGRAM}, and that row carries no programme lifecycle state. Without
 * this lookup an appointment to a SUSPENDED, CLOSED or ARCHIVED programme resolves as a
 * live programme context indistinguishable from an ACTIVE one — the registry's whole
 * status vocabulary would have no effect on authority.</p>
 *
 * <p>The whole registry is fetched once per resolution rather than one call per
 * appointment: programmes are few, and a list response lets us tell "WGV is unreachable"
 * (null) apart from "this programme id is not in the registry" (present list, absent id).
 * {@link WorkforceGovernanceClient#getJson} collapses both into null, and for an authority
 * decision those two must not read the same.</p>
 */
@Component
public class ProgrammeRegistryLookup {

    private static final Logger log = LoggerFactory.getLogger(ProgrammeRegistryLookup.class);

    /** Programmes change on an administrative cadence, not a request cadence. */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final WorkforceGovernanceClient governanceClient;

    private volatile Map<String, Programme> cached;
    private volatile Instant cachedAt;

    public ProgrammeRegistryLookup(WorkforceGovernanceClient governanceClient) {
        this.governanceClient = governanceClient;
    }

    /**
     * A programme as the registry describes it.
     *
     * @param id     programme id
     * @param name   display name, may be null if the registry row is incomplete
     * @param status ACTIVE | SUSPENDED | CLOSED | ARCHIVED
     */
    public record Programme(String id, String name, String status) {

        /** CLOSED and ARCHIVED programmes cannot confer authority — the programme is over. */
        public boolean confersNoAuthority() {
            return "CLOSED".equalsIgnoreCase(status) || "ARCHIVED".equalsIgnoreCase(status);
        }

        public boolean isSuspended() {
            return "SUSPENDED".equalsIgnoreCase(status);
        }

        public boolean isActive() {
            return "ACTIVE".equalsIgnoreCase(status);
        }
    }

    /** The outcome of a registry read, distinguishing unreachable from absent. */
    public sealed interface Outcome {
        /** The registry answered and named this programme. */
        record Known(Programme programme) implements Outcome {}

        /** The registry answered and did NOT name this programme — a dangling reference. */
        record Unknown() implements Outcome {}

        /** The registry could not be read; status is unverified, not absent. */
        record Unavailable() implements Outcome {}
    }

    public Outcome lookup(String programmeId) {
        if (programmeId == null || programmeId.isBlank()) {
            return new Outcome.Unknown();
        }
        Map<String, Programme> registry = registry();
        if (registry == null) {
            return new Outcome.Unavailable();
        }
        Programme p = registry.get(programmeId);
        return p != null ? new Outcome.Known(p) : new Outcome.Unknown();
    }

    /** @return the registry keyed by id, or null when WGV could not be read. */
    private Map<String, Programme> registry() {
        Map<String, Programme> snapshot = cached;
        Instant at = cachedAt;
        if (snapshot != null && at != null && Duration.between(at, Instant.now()).compareTo(CACHE_TTL) < 0) {
            return snapshot;
        }

        JsonNode data;
        try {
            data = governanceClient.getJson("/v1/internal/governance/programmes");
        } catch (Exception e) {
            log.warn("Programme registry read failed: {}", e.getMessage());
            return null;
        }
        if (data == null || !data.isArray()) {
            // Null covers both transport failure and a non-2xx; either way the registry
            // was not read, so callers must treat status as unverified rather than absent.
            return null;
        }

        Map<String, Programme> built = new LinkedHashMap<>();
        for (JsonNode row : data) {
            String id = text(row, "id");
            if (id == null) {
                continue;
            }
            built.put(id, new Programme(id, text(row, "name"), text(row, "status")));
        }
        cached = built;
        cachedAt = Instant.now();
        return built;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
