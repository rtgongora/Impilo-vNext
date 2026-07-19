/**
 * Experience UI — Find Care (public gateway lane) types.
 *
 * These mirror the PII-safe JSON returned by the anonymous find-care lane:
 *   GET /internal/v1/public/gateway/find-care/search
 *   GET /internal/v1/public/gateway/find-care/facilities/{id}
 *
 * The shapes are copied faithfully from the BFF records
 * (FindCareOrchestrationService.CareResult / CareSearchResponse) and the TUSO
 * public facility profile (FacilityResponse). Nullable fields are nullable here
 * for a reason — the UI must render "unknown" honestly rather than fabricate a
 * value the register does not hold.
 */

/** A single PII-safe find-care result (registry-derived; no contacts, no internal ids). */
export interface CareResult {
  facilityId: number | null;
  facilityUuid: string | null;
  name: string | null;
  facilityType: string | null;
  level: string | null;
  district: string | null;
  province: string | null;
  /** True only when the facility carries an ACTIVE matching capability. */
  serviceMatch: boolean;
  matchedService: string | null;
  /** Road (Ndila) or straight-line metres; null when no origin was shared / no coordinates. */
  distanceMeters: number | null;
  /** Routed ETA in minutes; null when routing is unavailable (never fabricated). */
  etaMinutes: number | null;
  /** Register status string (e.g. OPERATIONAL) — NOT a live "open now" signal. */
  operationalStatus: string | null;
  /** Always true from the search summary: register status is stale, not a live signal. */
  operationalStatusUnverified: boolean;
  /** Live open/closed — not derivable from the search summary; always null here. */
  openNow: boolean | null;
  /** Per-facility telemedicine — not held in the register yet; always null here. */
  telemedicineAvailable: boolean | null;
  latitude: number | null;
  longitude: number | null;
}

/**
 * A real, ACTIVE virtual-care option (TUSO virtual-service registry truth). Present only when a live
 * virtual service exists — the shell renders "Start virtual care" against these and never fabricates
 * one. Not attached to a specific facility card (per-facility telemedicine is not held in the register).
 */
export interface VirtualCareOption {
  reference: string | null;
  name: string | null;
  level: string | null;
  province: string | null;
  serviceLines: string[];
}

/** The orchestrated search response: interpretation, ranked results, and honest notes. */
export interface CareSearchResponse {
  interpretedService: string | null;
  interpretedTokens: string[];
  telemedicineRequested: boolean;
  emergencyIntent: boolean;
  accessibilityRequested: boolean;
  distanceAvailable: boolean;
  /** True only when at least one ACTIVE virtual service was found (never fabricated). */
  virtualCareAvailable: boolean;
  /** Live virtual-care options (empty when none are ACTIVE / matched). */
  virtualCare: VirtualCareOption[];
  /** Honest, human-readable caveats (no location, stale hours, straight-line distance…). */
  notes: string[];
  results: CareResult[];
  page: number;
  size: number;
  total: number;
}

/** A capability the facility actually offers (TUSO registry truth). */
export interface FacilityCapability {
  id: number | null;
  capabilityCode: string | null;
  capabilityType: string | null;
  name: string | null;
  ziboValidated: boolean;
  active: boolean;
  /** Free-form operating-hours map, when the register holds it. */
  operatingHours: Record<string, unknown> | null;
  /** Free-form metadata; may carry a service-point / "report to" hint. */
  metadata: Record<string, unknown> | null;
}

/** Disclosure-limited operating-model facet. */
export interface FacilityOperatingModel {
  facilityTier: string | null;
  deploymentMode: string | null;
  continuityClass: string | null;
  workflowArchetype: string | null;
}

/** Disclosure-limited public facility profile (TUSO FacilityResponse, redacted). */
export interface FacilityProfile {
  id: number | null;
  facilityUuid: string | null;
  name: string | null;
  facilityCode: string | null;
  facilityType: string | null;
  province: string | null;
  district: string | null;
  latitude: number | null;
  longitude: number | null;
  status: string | null;
  operationalStatus: string | null;
  level: string | null;
  facilityCategory: string | null;
  operatingModel: FacilityOperatingModel | null;
  capabilities: FacilityCapability[] | null;
  /**
   * Honest provenance/verification disclosure for regulator-listed records (HPA
   * enrichment). Optional — present when the facility carries a regulatory source
   * (e.g. `sourceSystem="HPA"`). The citizen surface must NEVER imply that a
   * regulatory listing proves the facility is currently open; always date the status.
   */
  sourceSystem?: string | null;
  sourceEffectiveDate?: string | null;
  verificationStatus?: string | null;
  profileIncomplete?: boolean | null;
  siteUnresolved?: boolean | null;
}

/**
 * A verified health professional who genuinely practises at a facility (Varapi register truth).
 *
 * Consumes GET /internal/v1/public/gateway/find-care/facilities/{id}/practitioners. Every field is
 * nullable on purpose — the roster only carries what Varapi has fully confirmed, and the UI must
 * render "unknown" honestly rather than fabricate a value the register does not hold. There is NO
 * availability / slot / "open now" information in this shape, and none must ever be inferred from it.
 */
export interface VerifiedProvider {
  displayName: string | null;
  profession: string | null;
  cadre: string | null;
  roleTitle: string | null;
  registrationNumber: string | null;
  registerStatus: string | null;
  /** ISO date string or null. */
  registeredSince: string | null;
  /** ISO date string or null. */
  registrationExpiryDate: string | null;
  licenceStatus: string | null;
  /** ISO date string or null. */
  licenceValidFrom: string | null;
  /** ISO date string or null. */
  licenceValidTo: string | null;
  registeringAuthority: string | null;
}

/** The verified-provider roster response for a facility (only fully confirmed register entries). */
export interface FacilityPractitionersResponse {
  providers: VerifiedProvider[];
}

/** Standard error envelope the lane returns for VALIDATION / RATE_LIMITED / FIND_CARE_UNAVAILABLE. */
export interface FindCareErrorEnvelope {
  error?: { code?: string; message?: string };
}
