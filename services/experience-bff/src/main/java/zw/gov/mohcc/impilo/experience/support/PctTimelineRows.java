package zw.gov.mohcc.impilo.experience.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composes PCT's journey-grouped timeline into the two flat resources the shell declares.
 *
 * <p>PCT's {@code /patient/{cpid}/timeline} returns journeys with their encounters nested inside:
 * {@code [{"journey": {...}, "encounters": [...]}]}. Two BFF endpoints read it — {@code
 * /internal/v1/encounters} and {@code /internal/v1/timeline} — and both passed it through
 * unchanged. Their hooks declare flat rows, so `encounter.attributes.status` dereferenced
 * `undefined` on any patient with at least one journey. That is every patient who has ever been
 * seen, which is why this could not be caught by a screen that renders — it fails on real data
 * and passes on the empty case.
 *
 * <p>This is composition, not renaming: the encounter list needs the encounters lifted out of
 * their journeys, and the timeline needs events derived from journeys <em>and</em> encounters.
 * Only fields PCT actually returns are used — nothing here invents an event that did not happen.
 */
public final class PctTimelineRows {

    private PctTimelineRows() {
    }

    /**
     * Lifts every encounter out of its journey into the flat {@code EncounterResource} the shell
     * declares, aliasing PCT's entity field names onto the ones the hook reads.
     *
     * <p>The aliases are real renames, not guesses: PCT's {@code EncounterEntity} calls the
     * subject {@code subjectCpid}, the clinician {@code assignedProviderId} and the end
     * {@code endedAt}, while the hook types those as {@code patientId}, {@code providerId} and
     * {@code closedAt}. Both spellings are emitted so neither side has to move first.
     *
     * <p>{@code journeyId} is carried through deliberately — flattening loses the grouping, and
     * callers that need to regroup must not have to ask PCT a second time.
     */
    public static List<Map<String, Object>> encounterRows(JsonNode timeline) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode entry : JsonApiRows.items(timeline)) {
            JsonNode journey = entry.get("journey");
            for (JsonNode encounter : JsonApiRows.items(entry.get("encounters"))) {
                Map<String, Object> attributes = JsonApiRows.attributesOf(encounter);

                alias(attributes, encounter, "subjectCpid", "patientId", "patient_id");
                alias(attributes, encounter, "assignedProviderId", "providerId", "provider_id");
                alias(attributes, encounter, "endedAt", "closedAt", "closed_at");

                // PCT — the system of record for encounter state — only ever sets STARTED,
                // COMPLETED or ON_HOLD. The shell was asking for "IN_PROGRESS" or "ACTIVE" in 26
                // places, so `activeEncounter` resolved to undefined for every patient in the
                // country: the banner showed no open encounter while one was open, and screens
                // that gate on having an encounter silently refused to offer it.
                //
                // PCT's vocabulary stays canonical and is passed through untouched. What the shell
                // actually means by "active" is "not finished", which includes ON_HOLD — a held
                // encounter is still open — so that intent is derived here rather than left to 26
                // call sites to enumerate and drift apart on.
                String status = JsonApiRows.text(encounter, "status");
                JsonApiRows.putBoth(attributes, "is_open", "isOpen",
                        status != null && !"COMPLETED".equalsIgnoreCase(status));
                if (journey != null && !attributes.containsKey("journeyId")) {
                    JsonApiRows.putBoth(attributes, "journey_id", "journeyId",
                            JsonApiRows.text(journey, "journeyId"));
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", firstNonNull(
                        JsonApiRows.text(encounter, "encounterRef"),
                        JsonApiRows.text(encounter, "encounter_ref"),
                        JsonApiRows.text(encounter, "id")));
                row.put("type", "encounter");
                row.put("attributes", attributes);
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(
                (Map<String, Object> row) -> occurredAt(row, "startedAt"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    /**
     * Derives timeline events from the same payload.
     *
     * <p>Every event here is anchored to something PCT recorded: a journey was opened, a journey
     * was opened <em>from a referral</em>, an encounter started. Nothing is synthesised to fill
     * the page — an empty clinical history must read as empty, because a timeline that invents
     * entries is worse than one that shows none.
     */
    public static List<Map<String, Object>> timelineEntries(JsonNode timeline) {
        List<Map<String, Object>> entries = new ArrayList<>();

        for (JsonNode entry : JsonApiRows.items(timeline)) {
            JsonNode journey = entry.get("journey");
            if (journey != null && journey.isObject()) {
                String journeyId = JsonApiRows.text(journey, "journeyId");
                String patientCpid = JsonApiRows.text(journey, "patientCpid");
                String openedAt = firstNonNull(
                        JsonApiRows.text(journey, "createdAt"),
                        JsonApiRows.text(journey, "startedAt"));

                // A journey opened from a referral is the referral event on this timeline —
                // the timeline page filters on eventType === "REFERRAL" to surface exactly this.
                String referralId = JsonApiRows.text(journey, "referralId");
                if (referralId != null) {
                    entries.add(event(
                            "referral:" + referralId, patientCpid, null, "REFERRAL",
                            "Referral received", JsonApiRows.text(journey, "referralSource"),
                            "pct_journey", journeyId, openedAt));
                }

                entries.add(event(
                        "journey:" + journeyId, patientCpid, null, "JOURNEY_OPENED",
                        "Care journey opened", JsonApiRows.text(journey, "journeyType"),
                        "pct_journey", journeyId, openedAt));
            }

            for (JsonNode encounter : JsonApiRows.items(entry.get("encounters"))) {
                String encounterRef = firstNonNull(
                        JsonApiRows.text(encounter, "encounterRef"),
                        JsonApiRows.text(encounter, "id"));
                entries.add(event(
                        "encounter:" + encounterRef,
                        JsonApiRows.text(encounter, "subjectCpid"),
                        encounterRef,
                        "ENCOUNTER",
                        firstNonNull(JsonApiRows.text(encounter, "encounterType"), "Encounter"),
                        JsonApiRows.text(encounter, "status"),
                        "pct_encounter", encounterRef,
                        JsonApiRows.text(encounter, "startedAt")));
            }
        }

        entries.sort(Comparator.comparing(
                (Map<String, Object> row) -> occurredAt(row, "occurredAt"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return entries;
    }

    private static Map<String, Object> event(String id, String patientId, String encounterId,
                                             String eventType, String title, String description,
                                             String sourceType, String sourceId, String occurredAt) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        JsonApiRows.putBoth(attributes, "patient_id", "patientId", patientId);
        JsonApiRows.putBoth(attributes, "encounter_id", "encounterId", encounterId);
        JsonApiRows.putBoth(attributes, "event_type", "eventType", eventType);
        attributes.put("title", title);
        attributes.put("description", description);
        JsonApiRows.putBoth(attributes, "source_type", "sourceType", sourceType);
        JsonApiRows.putBoth(attributes, "source_id", "sourceId", sourceId);
        // PCT's timeline payload carries no actor name. Leaving this null is the honest answer;
        // filling it with the provider id would put an identifier where the UI prints a person.
        JsonApiRows.putBoth(attributes, "actor_name", "actorName", null);
        JsonApiRows.putBoth(attributes, "occurred_at", "occurredAt", occurredAt);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("type", "timeline_entry");
        row.put("attributes", attributes);
        return row;
    }

    private static void alias(Map<String, Object> attributes, JsonNode source,
                              String from, String camelTo, String snakeTo) {
        // `has`, not `hasNonNull`: the hook types closedAt as `string | null`, so an open encounter
        // must answer null rather than omit the key. An absent key reads as `undefined`, which is
        // a different thing from "this encounter has not closed yet".
        if (source.has(from)) {
            attributes.putIfAbsent(camelTo, source.get(from));
            attributes.putIfAbsent(snakeTo, source.get(from));
        }
    }

    @SuppressWarnings("unchecked")
    private static String occurredAt(Map<String, Object> row, String key) {
        Object attributes = row.get("attributes");
        if (!(attributes instanceof Map)) {
            return null;
        }
        Object value = ((Map<String, Object>) attributes).get(key);
        if (value == null) {
            return null;
        }
        return value instanceof JsonNode node ? (node.isNull() ? null : node.asText()) : value.toString();
    }

    private static String firstNonNull(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
