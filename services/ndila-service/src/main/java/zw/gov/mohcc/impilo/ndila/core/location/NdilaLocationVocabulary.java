package zw.gov.mohcc.impilo.ndila.core.location;

/**
 * The one spelling of a facility location (HAR W4).
 *
 * <p>Three writers put facility rows into {@code ndila_locations} and one of them disagreed with
 * the other two: the V003 seed migration wrote {@code owner_service='tuso-service'} and
 * {@code location_type='FACILITY'}, while both runtime writers wrote {@code 'TUSO'} and
 * {@code 'HEALTH_FACILITY'}.</p>
 *
 * <p>That is not cosmetic. {@code NdilaSpatialSearchService} normalises a caller's
 * {@code FACILITY}/{@code CLINIC}/{@code HOSPITAL} <em>towards</em> {@code HEALTH_FACILITY} before
 * querying, so rows stored as {@code FACILITY} can never match a proximity search — and those were
 * the rows that had coordinates. V005 normalised the data; these constants exist so the next
 * writer cannot re-open the split by typing a literal.</p>
 */
public final class NdilaLocationVocabulary {

    /** Owner service for anything whose truth lives in the tuso facility registry. */
    public static final String OWNER_SERVICE_TUSO = "TUSO";

    /** Owner entity type for a registered health facility. */
    public static final String OWNER_ENTITY_TYPE_FACILITY = "FACILITY";

    /**
     * Canonical {@code location_type} for a health facility. Note this is NOT the string a caller
     * passes to search — callers may say {@code FACILITY}, and the alias map converts it to this.
     */
    public static final String LOCATION_TYPE_HEALTH_FACILITY = "HEALTH_FACILITY";

    private NdilaLocationVocabulary() {
    }
}
