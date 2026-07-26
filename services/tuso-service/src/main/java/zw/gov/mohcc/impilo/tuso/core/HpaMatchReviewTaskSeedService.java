package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns HPA's 591 disputed identity matches into human review tasks (HAR W7 step 2).
 *
 * <p><b>Why this replaced a feed rebuild.</b> The plan of record was to rebuild the bundle's
 * current↔legacy join to recover ~3,100 missing localities. Reading the crosswalk's own
 * {@code MatchStatus} breakdown showed there is no such gap: 3,442 current institutions have no
 * legacy record at all, 2,294 were matched with certainty and already enriched, and the remaining
 * <b>591 were deliberately withheld</b> because the matcher flagged them
 * {@code REVIEW_EXACT_REG_IDENTITY_CONFLICT}, {@code REVIEW_EXACT_REG_POSSIBLE_RENAME},
 * {@code REVIEW_EXACT_NAME_PROVINCE_REG_CHANGED} or {@code AMBIGUOUS_REGISTRATION_KEY}. The join is
 * conservative, not lossy.</p>
 *
 * <p><b>So the recovery is a person, not an algorithm.</b> Overriding an identity conflict in bulk
 * would attach a street address to a facility the source system explicitly said it was unsure
 * about — worse than lacking the address, because it discards a warning rather than merely missing
 * data. A registrar looking at "HPA lists institution X, the legacy record says Y, the matcher
 * suspects a rename" resolves it in seconds with knowledge no matcher has.</p>
 *
 * <p>Each task carries the evidence the reviewer needs and states the consequence of a decision, so
 * the queue is workable without opening the bundle. Nothing here writes a locality, an address or a
 * coordinate — resolving the task is what releases the data, through a human.</p>
 */
@Service
public class HpaMatchReviewTaskSeedService {

    private static final Logger log = LoggerFactory.getLogger(HpaMatchReviewTaskSeedService.class);

    /** The statuses the bundle's matcher withheld for human adjudication. */
    static final Set<String> DISPUTED_MATCH_STATUSES = Set.of(
            "REVIEW_EXACT_REG_IDENTITY_CONFLICT",
            "REVIEW_EXACT_REG_POSSIBLE_RENAME",
            "REVIEW_EXACT_NAME_PROVINCE_REG_CHANGED",
            "AMBIGUOUS_REGISTRATION_KEY");

    static final String TASK_TYPE = "VERIFICATION";
    static final String DIMENSION = "identity";
    static final String RESPONSIBLE_ACTOR = "REGULATOR";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public HpaMatchReviewTaskSeedService(JdbcTemplate jdbc,
                                         TransactionTemplate transactionTemplate,
                                         ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    public record SeedSummary(int crosswalkRows, int disputed, int tasksCreated,
                              int facilityNotFound, int alreadyPresent, boolean dryRun) {}

    /** One disputed match, reduced to what a reviewer actually needs to decide. */
    record DisputedMatch(String matchStatus, String matchMethod, String matchConfidence,
                         String institutionId, String institutionName, String currentRegNumber,
                         String legacyName, String legacyRegNumber, String legacyLocality,
                         String legacyAddress) {}

    public SeedSummary seedFromCrosswalk(UUID tenantId, String crosswalkPath, boolean dryRun) {
        Path path = Path.of(crosswalkPath);
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("Crosswalk not readable: " + crosswalkPath);
        }
        List<DisputedMatch> disputed = new ArrayList<>();
        int total = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                return new SeedSummary(0, 0, 0, 0, 0, dryRun);
            }
            List<String> columns = parseCsvLine(stripBom(header));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                total++;
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size() && i < values.size(); i++) {
                    row.put(columns.get(i), values.get(i));
                }
                String status = trimOrNull(row.get("MatchStatus"));
                if (status == null || !DISPUTED_MATCH_STATUSES.contains(status)) {
                    continue;
                }
                disputed.add(new DisputedMatch(
                        status,
                        trimOrNull(row.get("MatchMethod")),
                        trimOrNull(row.get("MatchConfidence")),
                        trimOrNull(row.get("CurrentInstitutionId")),
                        trimOrNull(row.get("CurrentInstitutionName")),
                        trimOrNull(row.get("CurrentRegistrationNumber")),
                        trimOrNull(row.get("LegacySQLName")),
                        trimOrNull(row.get("LegacySQLRegistrationNumber")),
                        trimOrNull(row.get("LegacyLocality")),
                        trimOrNull(row.get("LegacyPhysicalAddress"))));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Crosswalk read failed: " + e.getMessage(), e);
        }

        int created = 0, notFound = 0, present = 0;
        for (DisputedMatch match : disputed) {
            Long facilityId = resolveFacilityId(tenantId, match.institutionId());
            if (facilityId == null) {
                notFound++;
                continue;
            }
            String required = requiredInformation(match);
            Integer exists = jdbc.queryForObject(
                    "SELECT count(*) FROM tuso.facility_data_gap_task "
                            + " WHERE facility_id = ? AND task_type = ? AND required_information = ?",
                    Integer.class, facilityId, TASK_TYPE, required);
            if (exists != null && exists > 0) {
                present++;
                continue;
            }
            if (!dryRun) {
                final Long fid = facilityId;
                transactionTemplate.executeWithoutResult(status -> jdbc.update(
                        "INSERT INTO tuso.facility_data_gap_task "
                                + "(tenant_id, facility_id, task_type, dimension, required_information, "
                                + " why_required, responsible_actor, accepted_evidence, priority, "
                                + " visibility, status, resolution_history) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'INTERNAL', 'OPEN', CAST(? AS jsonb))",
                        tenantId, fid, TASK_TYPE, DIMENSION, required,
                        whyRequired(match), RESPONSIBLE_ACTOR, acceptedEvidence(match),
                        priorityFor(match.matchStatus()), evidenceJson(match)));
            }
            created++;
        }
        log.info("HPA match-review seeding: crosswalk={} disputed={} created={} facilityNotFound={} "
                        + "alreadyPresent={} dryRun={}",
                total, disputed.size(), created, notFound, present, dryRun);
        return new SeedSummary(total, disputed.size(), created, notFound, present, dryRun);
    }

    /**
     * An identity conflict is the dangerous one — two facilities may be confused with each other, so
     * it outranks a suspected rename, where the risk is a stale name rather than a wrong building.
     */
    static String priorityFor(String matchStatus) {
        return "REVIEW_EXACT_REG_IDENTITY_CONFLICT".equals(matchStatus) ? "HIGH" : "MEDIUM";
    }

    /** Unique per facility per disputed legacy record, so a re-run cannot duplicate the task. */
    static String requiredInformation(DisputedMatch match) {
        return "Confirm whether legacy record " + orUnknown(match.legacyRegNumber())
                + " is the same facility (" + match.matchStatus() + ")";
    }

    static String whyRequired(DisputedMatch match) {
        return switch (match.matchStatus()) {
            case "REVIEW_EXACT_REG_IDENTITY_CONFLICT" -> "The registration number matches a legacy "
                    + "record whose name and details disagree with the HPA listing. Until a person "
                    + "confirms these are the same facility, the legacy address and locality are "
                    + "withheld — attaching them to the wrong facility would send people to the "
                    + "wrong building.";
            case "REVIEW_EXACT_REG_POSSIBLE_RENAME" -> "The registration number matches a legacy "
                    + "record under a different name. If this is a rename, the legacy address and "
                    + "locality can be adopted; if it is a re-issued number, they must not be.";
            case "REVIEW_EXACT_NAME_PROVINCE_REG_CHANGED" -> "Name and province match a legacy "
                    + "record but the registration number differs, so the match cannot be made on "
                    + "the number alone.";
            case "AMBIGUOUS_REGISTRATION_KEY" -> "The registration number resolves to more than one "
                    + "legacy record, so no single match can be chosen automatically.";
            default -> "The current↔legacy match was flagged for human adjudication.";
        };
    }

    static String acceptedEvidence(DisputedMatch match) {
        return "A registrar's confirmation that HPA institution \"" + orUnknown(match.institutionName())
                + "\" (reg " + orUnknown(match.currentRegNumber()) + ") is or is not the same facility as "
                + "legacy record \"" + orUnknown(match.legacyName()) + "\" (reg "
                + orUnknown(match.legacyRegNumber()) + ")"
                + (match.legacyLocality() != null
                        ? ". Confirming releases the legacy locality"
                          + (match.legacyAddress() != null ? " and street address." : ".")
                        : ".");
    }

    private String evidenceJson(DisputedMatch match) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("seededFrom", "HPA_CURRENT_LEGACY_CROSSWALK");
        entry.put("matchStatus", match.matchStatus());
        entry.put("matchMethod", match.matchMethod());
        entry.put("matchConfidence", match.matchConfidence());
        entry.put("legacyName", match.legacyName());
        entry.put("legacyRegistrationNumber", match.legacyRegNumber());
        entry.put("withheldLocality", match.legacyLocality() != null);
        entry.put("withheldAddress", match.legacyAddress() != null);
        try {
            return "[" + objectMapper.writeValueAsString(entry) + "]";
        } catch (Exception e) {
            return "[]";
        }
    }

    private Long resolveFacilityId(UUID tenantId, String institutionId) {
        if (institutionId == null) {
            return null;
        }
        try {
            return jdbc.queryForObject(
                    "SELECT f.id FROM tuso.facility f "
                            + "  JOIN tuso.facility_identifier i ON i.facility_id = f.id "
                            + " WHERE f.tenant_id = ? AND i.system = ? AND i.value = ? LIMIT 1",
                    Long.class, tenantId, HpaEnrichmentImportService.SYS_HPA_INSTITUTION_ID, institutionId);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Minimal CSV reader: quoted fields with embedded commas and doubled quotes ----

    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /** The bundle's CSVs are UTF-8 with a BOM; an unstripped BOM corrupts the first column name. */
    static String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '﻿' ? value.substring(1) : value;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "N/A".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private static String orUnknown(String value) {
        return value == null ? "unknown" : value;
    }
}
