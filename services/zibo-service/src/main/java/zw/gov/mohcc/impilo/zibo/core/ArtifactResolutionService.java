package zw.gov.mohcc.impilo.zibo.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;

/**
 * The single place that answers "which artifact is current for this canonical URL".
 *
 * <p>Three callers each re-implemented this and each got it wrong the same way —
 * {@code ValidationEngine}, {@code MedicineRegistryService} and
 * {@code ArtifactRepository.findAllVersions} all selected by clock
 * ({@code max(comparing(publishedAt))}, {@code createdAt DESC}). Consolidating them means the
 * ordering rule, the effective window and the national fallback are fixed once.</p>
 *
 * <h3>What it fixes</h3>
 * <ol>
 *   <li><b>Ordering by version, not by clock.</b> Publishing a 1.0.1 correction after 1.1.0
 *       silently started serving the older content. See {@link VersionOrdering}.</li>
 *   <li><b>The effective window is honoured.</b> {@code effective_start}/{@code effective_end} were
 *       added by {@code V003} and read by nothing at all.</li>
 *   <li><b>National terminology resolves from either plane.</b> Every lookup was
 *       {@code findByTenantId…}, so a national registry seeded only in the REGISTRY plane was
 *       invisible to the CARE plane. That is why {@code V007} inserts ATC twice.</li>
 * </ol>
 *
 * <h3>The plane boundary is deliberate; the seeding asymmetry is not</h3>
 * <p>The two tenants are a real architectural boundary — see {@code PublicTenants} in
 * experience-bff: REGISTRY holds national reference data that predates any care episode, CARE holds
 * what an encounter writes. Terminology is national reference data, so it belongs in REGISTRY and
 * every plane should be able to read it. Falling back is therefore <em>correct</em>, not a
 * workaround — but it is also how a genuine seeding error hides, so every fallback increments
 * {@code zibo_national_fallback_total}. A metric that never fires means the content is where it
 * should be; one that fires constantly means someone seeded into the wrong plane.</p>
 */
@Service
public class ArtifactResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactResolutionService.class);

    private final ArtifactRepository artifactRepository;
    private final MeterRegistry meterRegistry;

    /**
     * The plane national governed terminology lives in.
     *
     * <p>Mirrors {@code PublicTenants.REGISTRY_PLANE}. Deliberately a local constant rather than a
     * dependency on experience-bff: a registry service must not import a BFF to know its own
     * authority plane.</p>
     */
    private final UUID nationalPlane;

    public ArtifactResolutionService(
            ArtifactRepository artifactRepository,
            MeterRegistry meterRegistry,
            @Value("${zibo.terminology.national-plane-tenant-id:00000000-0000-0000-0000-000000000001}")
            String nationalPlaneTenantId) {
        this.artifactRepository = artifactRepository;
        this.meterRegistry = meterRegistry;
        this.nationalPlane = UUID.fromString(nationalPlaneTenantId);
    }

    /** Where an artifact was found, so callers and telemetry can tell local from national. */
    public enum ResolutionPlane { TENANT, NATIONAL }

    public record Resolved(ArtifactEntity artifact, ResolutionPlane plane) {}

    /**
     * The artifact currently in force for a canonical URL: highest version whose effective window
     * contains now, preferring the requesting tenant over the national plane.
     */
    @Transactional(readOnly = true)
    public Optional<Resolved> resolveCurrent(UUID tenantId, String canonicalUrl, ArtifactType fhirType) {
        List<ArtifactEntity> candidates = artifactRepository.findCandidatesAcrossPlanes(
                canonicalUrl, planesFor(tenantId), fhirType);

        OffsetDateTime now = OffsetDateTime.now();

        // Already ordered by version_sort_key DESC, so the first match in each plane is the highest.
        Optional<ArtifactEntity> local = candidates.stream()
                .filter(a -> tenantId.equals(a.getTenantId()))
                .filter(a -> a.getStatus() == ArtifactStatus.PUBLISHED)
                .filter(a -> inEffect(a, now))
                .findFirst();
        if (local.isPresent()) {
            return Optional.of(new Resolved(local.get(), ResolutionPlane.TENANT));
        }

        Optional<ArtifactEntity> national = candidates.stream()
                .filter(a -> nationalPlane.equals(a.getTenantId()))
                .filter(a -> a.getStatus() == ArtifactStatus.PUBLISHED)
                .filter(a -> inEffect(a, now))
                .findFirst();
        if (national.isPresent()) {
            countFallback(canonicalUrl);
            log.debug("ZIBO: {} resolved from the national plane for tenant {} — governed "
                    + "terminology is national, so this is expected; a high rate is not",
                    canonicalUrl, tenantId);
            return Optional.of(new Resolved(national.get(), ResolutionPlane.NATIONAL));
        }

        return Optional.empty();
    }

    /**
     * An exact version, whatever its status.
     *
     * <p>Deliberately resolves DEPRECATED and RETIRED versions. A clinical record captured against
     * ICD-10 in 2025 must still be interpretable in 2030, and refusing to serve the vocabulary that
     * was in force when it was written would make the record unreadable rather than safe.</p>
     */
    @Transactional(readOnly = true)
    public Optional<Resolved> resolveVersion(UUID tenantId, String canonicalUrl, String version,
                                             ArtifactType fhirType) {
        List<ArtifactEntity> candidates = artifactRepository.findCandidatesAcrossPlanes(
                canonicalUrl, planesFor(tenantId), fhirType);

        Optional<ArtifactEntity> local = candidates.stream()
                .filter(a -> tenantId.equals(a.getTenantId()))
                .filter(a -> version.equals(a.getVersion()))
                .findFirst();
        if (local.isPresent()) {
            return Optional.of(new Resolved(local.get(), ResolutionPlane.TENANT));
        }

        Optional<ArtifactEntity> national = candidates.stream()
                .filter(a -> nationalPlane.equals(a.getTenantId()))
                .filter(a -> version.equals(a.getVersion()))
                .findFirst();
        if (national.isPresent()) {
            countFallback(canonicalUrl);
            return Optional.of(new Resolved(national.get(), ResolutionPlane.NATIONAL));
        }

        return Optional.empty();
    }

    /**
     * True when {@code now} falls inside the artifact's effective window.
     *
     * <p>A null bound is unbounded on that side. {@code effective_end} is exclusive, matching the
     * column comment added by {@code V003}.</p>
     */
    private static boolean inEffect(ArtifactEntity artifact, OffsetDateTime now) {
        OffsetDateTime start = artifact.getEffectiveStart();
        OffsetDateTime end = artifact.getEffectiveEnd();
        if (start != null && now.isBefore(start)) {
            return false;
        }
        return end == null || now.isBefore(end);
    }

    private List<UUID> planesFor(UUID tenantId) {
        return tenantId.equals(nationalPlane) ? List.of(nationalPlane) : List.of(tenantId, nationalPlane);
    }

    private void countFallback(String canonicalUrl) {
        Counter.builder("zibo_national_fallback_total")
                .description("Artifact resolutions served from the national plane because the "
                        + "requesting tenant had no copy. Expected for governed terminology; a high "
                        + "rate against a tenant-owned artifact means it was seeded into the wrong plane.")
                .tag("canonical_url", canonicalUrl)
                .register(meterRegistry)
                .increment();
    }

    /** The national plane, for callers that need to name it (e.g. seeding). */
    public UUID nationalPlane() {
        return nationalPlane;
    }
}
