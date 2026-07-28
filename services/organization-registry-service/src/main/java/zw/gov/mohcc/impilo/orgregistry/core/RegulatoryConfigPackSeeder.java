package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigPackEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigReleaseEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ConfigPackRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads version-controlled regulatory configuration packs into the Registry at startup.
 *
 * <p><b>A deploy never activates anything.</b> The seeder authors DRAFT versions and gathers them
 * into a DRAFT release, and stops. A human submits it; two humans approve it. If a deploy could
 * activate, then anyone with commit access to this directory would be a sole approver of fee and
 * penalty changes, and the four-eyes rail the Registry enforces in schema would be decoration.</p>
 *
 * <p><b>Drift is recorded, not fatal.</b> tuso's {@code InspectionContentSeeder} answers a checksum
 * mismatch by killing the service at startup. That is right for tuso and wrong here: org-registry
 * sits on the regulator login path — {@code WorkContextController} resolves regulatory appointments
 * through it — so a typo in a pack file would take away every regulator's ability to sign in. A
 * configuration mistake must not become an estate-wide outage.</p>
 *
 * <p>So the distrust is aimed precisely. The FILE is what changed, so the file is what stops being
 * trusted: the pack is marked {@code LOAD_FAILED} and nothing further is seeded from it until a
 * human resolves it. The RELEASE is untouched — it was approved by two people and is what live
 * cases are pinned to, and a load failure says nothing about it. The failure is loud in the log and
 * visible through {@code RegulatoryConfigPackHealthIndicator}.</p>
 */
@Component
@ConditionalOnProperty(value = "impilo.regulatory.config-packs.enabled", havingValue = "true",
        matchIfMissing = true)
public class RegulatoryConfigPackSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryConfigPackSeeder.class);
    private static final String SEEDER_ACTOR = "system:config-pack-seeder";

    private final RegulatoryConfigService configService;
    private final ConfigPackRepository packRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectMapper objectMapper;
    private final String location;
    private final UUID tenantId;

    /** Pack key -> failure reason, for packs that could not even be recorded in the database. */
    private final Map<String, String> unrecordedFailures = new LinkedHashMap<>();

    public RegulatoryConfigPackSeeder(
            RegulatoryConfigService configService,
            ConfigPackRepository packRepository,
            OrganizationRepository organizationRepository,
            ObjectMapper objectMapper,
            @Value("${impilo.regulatory.config-packs.location:classpath*:regulatory/councils/*/manifest.json}")
            String location,
            @Value("${impilo.regulatory.config-packs.tenant-id:00000000-0000-0000-0000-000000000001}")
            String tenantId) {
        this.configService = configService;
        this.packRepository = packRepository;
        this.organizationRepository = organizationRepository;
        this.objectMapper = objectMapper;
        this.location = location;
        this.tenantId = UUID.fromString(tenantId);
    }

    /** Packs that failed to load and could not be recorded against a row, for the health endpoint. */
    public Map<String, String> unrecordedFailures() {
        return Map.copyOf(unrecordedFailures);
    }

    @Override
    public void run(ApplicationArguments args) {
        Resource[] manifests;
        try {
            manifests = new PathMatchingResourcePatternResolver().getResources(location);
        } catch (Exception ex) {
            log.error("Unable to scan for regulatory configuration packs at {}", location, ex);
            return;
        }
        if (manifests.length == 0) {
            log.info("No regulatory configuration packs found at {}", location);
            return;
        }
        for (Resource manifest : manifests) {
            seedOne(manifest);
        }
    }

    private void seedOne(Resource manifestResource) {
        String packKey = "unknown";
        ConfigPackEntity pack = null;
        try {
            JsonNode manifest;
            String manifestHash;
            try (InputStream in = manifestResource.getInputStream()) {
                byte[] raw = in.readAllBytes();
                manifest = objectMapper.readTree(raw);
                manifestHash = sha256(normalise(raw));
            }
            packKey = text(manifest, "packKey");
            String organizationCode = text(manifest, "organizationCode");

            Optional<OrganizationEntity> organization =
                    organizationRepository.findByTenantIdAndCode(tenantId, organizationCode);
            if (organization.isEmpty()) {
                // No organisation means no pack row to record the failure against, so it is held in
                // memory for the health endpoint rather than disappearing into the log.
                fail(packKey, "No organisation with code '" + organizationCode + "' in tenant "
                        + tenantId + ". The pack names a regulator this environment does not have.");
                return;
            }

            pack = configService.createPack(tenantId, organization.get().getId(),
                    packKey, text(manifest, "label"), manifest.path("description").asText(null),
                    SEEDER_ACTOR);

            // Verify every file against the manifest BEFORE writing anything. A pack is loaded whole
            // or not at all; a half-seeded pack is a worse state than an unseeded one.
            List<JsonNode> definitions = new ArrayList<>();
            for (JsonNode entry : manifest.withArray("definitions")) {
                String file = text(entry, "file");
                String declared = text(entry, "sha256");
                Resource resource = manifestResource.createRelative("definitions/" + file);
                if (!resource.exists()) {
                    recordFailure(pack, manifestHash, "Manifest lists '" + file
                            + "', which is not in the pack.");
                    return;
                }
                byte[] raw;
                try (InputStream in = resource.getInputStream()) {
                    raw = in.readAllBytes();
                }
                String actual = sha256(normalise(raw));
                if (!actual.equals(declared)) {
                    recordFailure(pack, manifestHash, "'" + file + "' does not match its manifest "
                            + "checksum (manifest " + declared.substring(0, 12) + "…, file "
                            + actual.substring(0, 12) + "…). The file changed without the manifest "
                            + "being regenerated, so nothing further is seeded from this pack. The "
                            + "activated release is unaffected and continues to serve.");
                    return;
                }
                definitions.add(objectMapper.readTree(raw));
            }

            List<UUID> versionIds = new ArrayList<>();
            for (JsonNode definition : definitions) {
                versionIds.add(authorFrom(pack, definition, packKey));
            }

            JsonNode release = manifest.path("release");
            String releaseKey = text(release, "releaseKey");
            ConfigPackEntity loaded = pack;
            ConfigReleaseEntity draft = configService.listReleases(tenantId, pack.getId()).stream()
                    .filter(r -> releaseKey.equals(r.getReleaseKey()))
                    .findFirst()
                    .orElseGet(() -> configService.createRelease(tenantId, loaded.getId(), releaseKey,
                            release.path("label").asText(releaseKey),
                            release.path("notes").asText(null), SEEDER_ACTOR));

            if ("DRAFT".equals(draft.getLifecycleState())) {
                versionIds.forEach(id -> configService.addItem(tenantId, draft.getId(), id, SEEDER_ACTOR));
            } else {
                // The release has moved on under human hands. Re-seeding into it would either fail
                // against the frozen-contents trigger or quietly reopen an approved decision.
                log.info("Configuration release {}/{} is {} — leaving it alone; the seeder only "
                        + "prepares DRAFTs", packKey, releaseKey, draft.getLifecycleState());
            }

            markLoaded(pack, manifestHash, manifestResource.getDescription());
            log.info("Loaded regulatory configuration pack '{}' — {} definitions prepared as DRAFT "
                    + "release '{}'. Activation is a governed act and is NOT performed by deployment.",
                    packKey, versionIds.size(), releaseKey);

        } catch (Exception ex) {
            // Including the checksum-immutability refusal from authorVersion: a pack file whose
            // content changed under a fixed version is exactly the drift this guards against.
            if (pack != null) {
                recordFailure(pack, null, describe(ex));
            } else {
                fail(packKey, describe(ex));
            }
        }
    }

    private UUID authorFrom(ConfigPackEntity pack, JsonNode definition, String packKey) {
        List<RegulatoryConfigService.DependencySpec> dependencies = new ArrayList<>();
        for (JsonNode dependency : definition.withArray("dependencies")) {
            dependencies.add(new RegulatoryConfigService.DependencySpec(
                    text(dependency, "typeCode"), text(dependency, "definitionKey"),
                    dependency.path("optional").asBoolean(false)));
        }
        return configService.authorVersion(tenantId, pack.getId(),
                text(definition, "typeCode"), text(definition, "definitionKey"),
                definition.path("label").asText(null), text(definition, "semanticVersion"),
                definition.path("payload"),
                definition.path("selfServiceRoute").asText(null),
                definition.path("policyStatus").asText(null),
                definition.path("policyDecisionRef").asText(null),
                packKey + "/" + text(definition, "definitionKey"),
                dependencies, SEEDER_ACTOR).getId();
    }

    private void markLoaded(ConfigPackEntity pack, String manifestHash, String sourceRef) {
        pack.setLoadState("LOADED");
        pack.setLoadFailureReason(null);
        pack.setSourceManifestHash(manifestHash);
        pack.setSourceRef(sourceRef);
        pack.setLoadCheckedAt(OffsetDateTime.now());
        packRepository.save(pack);
    }

    private void recordFailure(ConfigPackEntity pack, String manifestHash, String reason) {
        pack.setLoadState("LOAD_FAILED");
        pack.setLoadFailureReason(reason);
        if (manifestHash != null) {
            pack.setSourceManifestHash(manifestHash);
        }
        pack.setLoadCheckedAt(OffsetDateTime.now());
        packRepository.save(pack);
        log.error("Regulatory configuration pack '{}' FAILED to load: {} — the service continues and "
                + "any activated release keeps serving; no further configuration is seeded from this "
                + "pack until the drift is resolved.", pack.getPackKey(), reason);
    }

    private void fail(String packKey, String reason) {
        unrecordedFailures.put(packKey, reason);
        log.error("Regulatory configuration pack '{}' FAILED to load: {} — the service continues.",
                packKey, reason);
    }

    private static String describe(Exception ex) {
        String message = ex.getMessage();
        return ex.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException("Configuration pack is missing required field '"
                    + field + "'");
        }
        return value.asText();
    }

    static String sha256(byte[] raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest(raw)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to checksum configuration pack content", ex);
        }
    }

    /** Normalises line endings so a checkout on another platform is not read as drift. */
    static byte[] normalise(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}
