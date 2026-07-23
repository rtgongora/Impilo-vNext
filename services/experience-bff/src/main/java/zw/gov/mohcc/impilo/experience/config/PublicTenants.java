package zw.gov.mohcc.impilo.experience.config;

/**
 * Canonical public-lane tenant identifiers — the named home for the two default
 * tenants the estate runs.
 *
 * <p><b>This is NOT a bug to be unified away.</b> The 2026-07-23 tenancy discovery
 * sweep (row counts across every service DB) showed the two UUIDs are a deliberate
 * <b>plane boundary</b>, not an accident:</p>
 *
 * <ul>
 *   <li><b>{@link #REGISTRY_PLANE}</b> ({@code …0000-0000-…0001}) — the
 *       registry / reference / identity plane: TUSO facility master (~7k facilities,
 *       ~16k capabilities), VARAPI provider register (~4k providers), NDILA locations
 *       (~8k), the HPA import pipeline, TSHEPO authz policy rules (304) and identity
 *       mappings. National reference data that predates any care episode.</li>
 *   <li><b>{@link #CARE_PLANE}</b> ({@code …4000-8000-…0001}) — the care / transaction
 *       plane: coverage, costing, PCT, OROS, pharmacy, inpatient, MSIKA flow, MUSHEX,
 *       inventory, DAIDZAI emergencies, SIMBA, learning content. Everything a real
 *       encounter, order, claim or plan writes.</li>
 * </ul>
 *
 * <p>A public GET lane therefore declares the plane its data lives in, rather than
 * assuming one "default" tenant. Most discovery reads (facilities, practitioners,
 * map, participation, ops status) are registry-plane; care-plane reads (emergency
 * status, patient-safety, marketplace listings, coverage catalogs, wellness/learning
 * content) use {@link #CARE_PLANE}. A handful of services are themselves split
 * (VARAPI: providers=registry, councils/licenses=care; VITO: mixed) — each such lane
 * picks the plane holding the rows it reads and says so at the call site.</p>
 *
 * <p>Collapsing the two planes into one tenant would re-key live authorization (304
 * policy rules) and every registry consumer on a running estate; it is intentionally
 * out of scope here and, if ever pursued, is its own carefully-staged program with
 * authz in scope. Cross-plane reads that cannot know the plane in advance (e.g. the
 * anonymous SOS status lookup) keep their reference-only service-side fallback as
 * defence in depth (daidzai {@code EmergencyService.findRequestByReference}).</p>
 */
public final class PublicTenants {

    private PublicTenants() {}

    /** Registry / reference / identity plane. National master data, pre-episode. */
    public static final String REGISTRY_PLANE = "00000000-0000-0000-0000-000000000001";

    /** Care / transaction plane. Encounters, orders, claims, plans, emergencies. */
    public static final String CARE_PLANE = "00000000-0000-4000-8000-000000000001";

    /** The pod all national-spine public lanes present. */
    public static final String PUBLIC_POD = "national-spine";
}
