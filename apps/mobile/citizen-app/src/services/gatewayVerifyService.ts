/**
 * Guest verification service — the public gateway "trust" pillar (no sign-in).
 *
 * Uses the anonymous {@link publicApiClient} against the gateway's permitAll register
 * lanes. These expose register facts only (no PII, no contact details, no disciplinary
 * detail); a miss is a uniform NOT_FOUND (no enumeration oracle):
 *
 *   GET /internal/v1/public/gateway/practitioners/verify/{registrationNumber}
 *   GET /internal/v1/public/gateway/facilities/search?query=&facilityType=&province=&district=&level=&page=&size=
 *   GET /internal/v1/public/gateway/facilities/{id}/profile
 *
 * Contract owners: experience-bff PublicGatewayPractitionerBffController (→ VARAPI) and
 * PublicGatewayFacilityBffController (→ TUSO).
 */
import { publicApiClient, ApiError } from "@impilo/mobile-api-client";

export interface PractitionerVerification {
  status?: string;
  displayName?: string;
  profession?: string;
  registerStatus?: string;
  registrationNumber?: string;
  certificateValidFrom?: string | null;
  certificateValidTo?: string | null;
  registeringAuthority?: string;
  [key: string]: unknown;
}

export interface PublicFacility {
  id?: string | number;
  name?: string;
  facilityType?: string;
  level?: string;
  district?: string;
  province?: string;
  status?: string;
  [key: string]: unknown;
}

export interface PublicFacilitySearchResult {
  data: PublicFacility[];
  meta?: { page?: number; size?: number; totalElements?: number; totalPages?: number };
}

export interface FacilitySearchQuery {
  query?: string;
  facilityType?: string;
  province?: string;
  district?: string;
  level?: string;
  page?: number;
  size?: number;
}

function unwrap<T>(payload: unknown): T {
  if (payload && typeof payload === "object" && "data" in (payload as Record<string, unknown>)) {
    const data = (payload as Record<string, unknown>).data;
    if (data && typeof data === "object" && "attributes" in (data as Record<string, unknown>)) {
      return (data as Record<string, unknown>).attributes as T;
    }
    return data as T;
  }
  return payload as T;
}

/**
 * Verify a practitioner registration number. A NOT_FOUND (200 with NOT_FOUND status, or
 * a 404) resolves to `null` so callers render an honest "not found" rather than an error.
 */
export async function verifyPractitioner(
  registrationNumber: string,
): Promise<PractitionerVerification | null> {
  try {
    const res = await publicApiClient.get(
      `/internal/v1/public/gateway/practitioners/verify/${encodeURIComponent(registrationNumber.trim())}`,
    );
    const result = unwrap<PractitionerVerification>(res.data);
    if (result && typeof result.status === "string" && result.status.toUpperCase() === "NOT_FOUND") {
      return null;
    }
    return result ?? null;
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** Search the national facility register (disclosure-limited public view). */
export async function searchPublicFacilities(
  query: FacilitySearchQuery,
): Promise<PublicFacilitySearchResult> {
  const params = new URLSearchParams();
  if (query.query) params.set("query", query.query.trim());
  if (query.facilityType) params.set("facilityType", query.facilityType);
  if (query.province) params.set("province", query.province);
  if (query.district) params.set("district", query.district);
  if (query.level) params.set("level", query.level);
  params.set("page", String(query.page ?? 0));
  params.set("size", String(query.size ?? 20));

  const res = await publicApiClient.get<PublicFacilitySearchResult>(
    `/internal/v1/public/gateway/facilities/search?${params.toString()}`,
  );
  const body = res.data as PublicFacilitySearchResult | undefined;
  return { data: Array.isArray(body?.data) ? body!.data : [], meta: body?.meta };
}

/** Read one facility's disclosure-limited public profile. */
export async function fetchPublicFacilityProfile(id: string | number): Promise<PublicFacility | null> {
  try {
    const res = await publicApiClient.get(
      `/internal/v1/public/gateway/facilities/${encodeURIComponent(String(id))}/profile`,
    );
    return unwrap<PublicFacility>(res.data);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}
