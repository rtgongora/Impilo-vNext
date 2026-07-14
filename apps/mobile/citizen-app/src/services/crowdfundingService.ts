/**
 * Crowdfunding (Fundraisers) Service — governed fundraiser cases via the BFF composition
 * at /internal/v1/citizen/fundraisers (Daidzai owns the CASE + verification lifecycle,
 * mushe owns the MONEY). Replaces the retired simba raw-JDBC stub (now HTTP 410).
 *
 * Lifecycle: DRAFT → PENDING_VERIFICATION → ACTIVE → FULFILLED | CANCELLED | REJECTED |
 * REVOKED. Only verified-ACTIVE cases are listed publicly and accept donations.
 */
import { apiClient } from "@impilo/mobile-api-client";
import { generateId } from "@impilo/mobile-trust";
import type { CrowdfundingCampaign } from "../types";

const V1 = "/internal/v1/citizen/fundraisers";

function toNumber(v: unknown): number {
  const n = typeof v === "number" ? v : Number(v ?? 0);
  return Number.isFinite(n) ? n : 0;
}

/** Normalize a Daidzai assistance case into the mobile campaign shape (amounts coerced). */
function normalize(raw: unknown): CrowdfundingCampaign {
  const r = (raw ?? {}) as Record<string, unknown>;
  return {
    id: String(r.id ?? ""),
    assistanceReference: r.assistanceReference as string | undefined,
    title: String(r.title ?? ""),
    story: (r.story as string | undefined) ?? "",
    category: (r.category as string | undefined) ?? "",
    targetAmount: toNumber(r.targetAmount),
    raisedAmount: toNumber(r.raisedAmount),
    currency: String(r.currency ?? "USD"),
    donorCount: toNumber(r.donorCount),
    verificationStatus: String(r.verificationStatus ?? "DRAFT"),
    shareToken: r.shareToken as string | undefined,
    attestationPublicToken: r.attestationPublicToken as string | undefined,
    subjectIdentityMode: r.subjectIdentityMode as string | undefined,
    createdAt: r.createdAt as string | undefined,
    endsAt: r.endsAt as string | undefined,
  };
}

/** Publicly listed fundraisers — verified-ACTIVE only (enforced server-side). */
export async function fetchCampaigns(): Promise<CrowdfundingCampaign[]> {
  const response = await apiClient.get<unknown>(V1);
  const rows = Array.isArray(response.data) ? response.data : [];
  return rows.map(normalize);
}

/** My fundraisers — any lifecycle status. */
export async function fetchMine(): Promise<CrowdfundingCampaign[]> {
  const response = await apiClient.get<unknown>(`${V1}/mine`);
  const rows = Array.isArray(response.data) ? response.data : [];
  return rows.map(normalize);
}

export interface FundraiserDetail {
  campaign: CrowdfundingCampaign;
  /** True when the live money view was unreachable — amounts are the latest snapshot. */
  moneyUnavailable?: boolean;
  shareUrl?: string;
  independentVerificationUrl?: string;
}

/** Case + composed money view + independent-verification pointer. */
export async function fetchDetail(id: string): Promise<FundraiserDetail> {
  const response = await apiClient.get<unknown>(`${V1}/${id}`);
  const d = (response.data ?? {}) as Record<string, unknown>;
  return {
    campaign: normalize(d.case),
    moneyUnavailable: d.moneyUnavailable === true || undefined,
    shareUrl: d.shareUrl as string | undefined,
    independentVerificationUrl: d.independentVerificationUrl as string | undefined,
  };
}

/**
 * Donate from MY wallet — real money movement, idempotent per attempt.
 * Success is ONLY a 2xx from the BFF; rejections (409 FUNDRAISER_NOT_OPEN, insufficient
 * funds from mushe) throw ApiError and must be surfaced honestly by the caller.
 */
export async function donate(
  fundraiserId: string,
  body: { amount: number; message?: string; isAnonymous?: boolean },
): Promise<{ contributionId?: string }> {
  const response = await apiClient.post<Record<string, unknown>>(
    `${V1}/${fundraiserId}/donate`,
    body,
    { headers: { "Idempotency-Key": generateId() } },
  );
  const d = (response.data ?? {}) as Record<string, unknown>;
  return { contributionId: (d.contributionId ?? d.id) as string | undefined };
}
